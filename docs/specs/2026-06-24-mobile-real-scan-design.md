# Mobile Real Scan Design

## 1. Goal

This design replaces the mobile H5 `模拟扫码` shortcuts with real QR scanning for:

- Inventory tag code scanning in mobile inbound.
- Outbound order QR scanning in mobile outbound with-order mode.
- Inventory tag code scanning in mobile outbound.
- Inventory tag code scanning in mobile inventory tag query.

Manual input remains available as a fallback because browser camera access depends on device permission and secure-context support.

## 2. Scope

In scope:

- Reuse the existing Vue mobile H5 routes under `/mobile`.
- Reuse the existing `html5-qrcode` dependency already present in the frontend.
- Support camera scanning from the rear camera when available.
- Support local image QR recognition as a fallback for environments where camera access is blocked.
- Keep existing mobile business APIs unchanged:
  - `POST /api/inventory/scan-inbound`
  - `GET /api/outbound-orders/no/{outboundNo}/qr-info`
  - `POST /api/outbound/pick-with-order`
  - `POST /api/outbound/pick-no-order`
  - inventory tag trace and lookup APIs.
- Keep manual inventory tag code and outbound order number input.

Out of scope:

- Native Android app work.
- PDA, scanner-gun, label-printer, or device SDK integration.
- Offline scanning cache.
- Persisting failed scan attempts to a new backend table.
- Changing inventory tag or outbound order QR encoding rules beyond frontend normalization.

## 3. Browser and Runtime Constraints

Camera scanning uses browser media APIs and therefore depends on:

- Browser camera permission.
- A secure context, such as HTTPS or browser-recognized localhost.
- A camera device that can focus on the printed or screen-displayed QR code.

If camera start fails, the mobile page must show a readable message and keep both `选择图片` and manual input usable. Native `BarcodeDetector` is not used as the primary implementation because it is still limited across major browsers; `html5-qrcode` is the project dependency used for QR decoding.

## 4. Architecture

### 4.1 Shared scanner component

Create a reusable mobile scanner component, tentatively named `MobileQrScanner`.

Responsibilities:

- Render a camera preview container.
- Start and stop `Html5Qrcode`.
- Decode QR content from camera frames.
- Decode QR content from a selected local image.
- Emit decoded text to the parent page.
- Emit scanner errors as readable UI messages.
- Stop the camera when closed, when a scan succeeds, and when the component unmounts.

The component must not call WMS business APIs. It only returns decoded text.

Suggested props:

- `readerId`: stable DOM id suffix for `Html5Qrcode`.
- `label`: short label such as `扫描库存标签码` or `扫描出库单二维码`.
- `disabled`: disables scan buttons during business submission.

Suggested events:

- `decoded(text)`: emitted after a QR value is decoded.
- `error(message)`: emitted when camera or image recognition fails.

### 4.2 Mobile page ownership

Business pages own the meaning of decoded text:

- `MobileInboundView.vue` treats decoded text as an inventory tag code.
- `MobileInventoryTagQueryView.vue` treats decoded text as an inventory tag code.
- `MobileOutboundView.vue` chooses meaning by mode and step:
  - with-order mode before order load: decoded text is normalized as an outbound order number.
  - with-order mode after order load: decoded text is an inventory tag code.
  - no-order mode: decoded text is an inventory tag code.

## 5. Decoded Text Normalization

The first implementation should support these QR payloads:

- Raw inventory tag code, for example `IT:v1:IN-...`.
- Raw outbound order number, for example `OUT-...`.
- Plain URL containing one of those values in the last path segment or a query parameter.

Normalization functions:

- `normalizeInventoryTagCode(text)`: trim whitespace and return the decoded inventory tag code.
- `normalizeOutboundNo(text)`: trim whitespace; if the payload is a URL, prefer an `outboundNo` query parameter, otherwise use the last non-empty path segment.

If normalization cannot identify the expected value, the page should keep the decoded text in the relevant input and let existing business validation return the final readable error.

## 6. Mobile Inbound Flow

1. User opens `/mobile/inbound`.
2. User taps `启动摄像头` or `选择图片`.
3. Scanner decodes an inventory tag QR.
4. Page fills `inventoryTagCode`.
5. Existing watch logic loads inventory tag preview.
6. User selects or leaves target location.
7. User confirms inbound.
8. Existing `scanInbound` API submits the business action.

Acceptance:

- A scanned `PRINTED` inventory tag can be submitted for inbound.
- Camera failure does not block manual input.
- Scan success stops the camera and shows the decoded inventory tag code.

## 7. Mobile Outbound Flow

### 7.1 With-order mode

1. User opens `/mobile/outbound` with `带单出库` selected.
2. Before loading an order, scanner label indicates outbound order QR scanning.
3. User scans an outbound order QR.
4. Page fills `outboundNo` and loads the order through `fetchQrInfo`.
5. After the order is loaded, scanner label switches to inventory tag scanning.
6. User scans a locked inventory tag.
7. Page fills `inventoryTagCode`, loads preview, and lets the user confirm outbound.
8. Existing `pickWithOrder` API submits the business action.
9. After success, order info and locked item list refresh.

Acceptance:

- A scanned outbound order QR can load the order without manual typing.
- A scanned locked inventory tag can complete with-order outbound.
- If the scanned tag does not belong to the loaded order, the existing backend error is shown.

### 7.2 No-order mode

1. User switches to `不带单出库`.
2. Scanner label indicates inventory tag scanning.
3. User scans an inventory tag.
4. Page fills `inventoryTagCode`, loads preview, and lets the user confirm outbound.
5. Existing `pickNoOrder` API submits the business action.

Acceptance:

- A scanned available inventory tag can complete no-order outbound.
- Locked, sealed, shipped, or invalid inventory tags show existing readable backend errors.

## 8. Mobile Inventory Tag Query Flow

1. User opens `/mobile/inventory-tag`.
2. User scans an inventory tag QR.
3. Page fills `inventoryTagCode`.
4. Page runs the existing query action automatically or leaves the value ready for explicit query.

Recommended behavior:

- Auto-query after scan success, because this page is read-only and scanning is the primary action.

Acceptance:

- A scanned inventory tag displays lifecycle, location, quantity, locked/sealed state, scan time, and movement number when available.
- Manual query still works after camera failure.

## 9. Error Handling

Scanner-level errors:

- Camera permission denied: show `摄像头权限被拒绝，请允许浏览器访问摄像头，或使用图片识别/手动输入。`
- Insecure context: show `当前访问方式不支持摄像头，请使用 HTTPS、localhost，或改用图片识别/手动输入。`
- No camera found: show `未检测到可用摄像头，请使用图片识别或手动输入。`
- QR not found in image: show `未能识别二维码，请更换图片或手动输入。`

Business-level errors:

- Keep using existing backend messages from API responses.
- Do not convert backend validation errors into scanner errors.

## 10. Testing and Verification

Automated checks:

- Add frontend tests for normalization helpers:
  - raw outbound order number.
  - outbound order URL with query parameter.
  - outbound order URL with path segment.
  - raw inventory tag code.
- Keep existing `mobileFlows.test.js` API wrapper tests passing.
- Run `cd frontend && npm test`.
- Run `cd frontend && npm run build` because this touches mobile UI and scanner dependency paths.

Manual checks:

- Mobile inbound camera scan.
- Mobile inbound image QR recognition.
- Mobile with-order outbound: scan outbound order QR, then scan inventory tag QR.
- Mobile no-order outbound: scan inventory tag QR.
- Mobile inventory tag query camera scan.
- Camera permission denied or unsupported context fallback.
- Manual input still works on all three mobile pages.

## 11. Documentation Updates

When implemented, update:

- `docs/tests/acceptence-tests/iter4/week4-fr-acceptance-test-steps.md` to replace the old statement that phone-side real camera scanning is not required.
- `docs/tests/acceptence-tests/iter4/week4-fr-acceptance-results.md` after re-running FR-05 acceptance.
- `docs/exec-plans/tech-debt-tracker.md` only if any browser/camera limitation remains outside this implementation.

No product debt update is required unless the QR payload format changes from raw code/order number to a new structured schema.
