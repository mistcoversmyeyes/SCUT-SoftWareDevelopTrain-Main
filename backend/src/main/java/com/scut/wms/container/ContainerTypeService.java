package com.scut.wms.container;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.scut.wms.common.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ContainerTypeService {
    private static final String ENABLED = "ENABLED";

    private final ContainerTypeMapper containerTypeMapper;

    public ContainerTypeService(ContainerTypeMapper containerTypeMapper) {
        this.containerTypeMapper = containerTypeMapper;
    }

    public List<ContainerType> listAll() {
        return containerTypeMapper.selectList(Wrappers.<ContainerType>lambdaQuery()
                .orderByAsc(ContainerType::getContainerCode));
    }

    @Transactional
    public ContainerType create(ContainerTypeRequest request) {
        ContainerType existing = containerTypeMapper.selectOne(Wrappers.<ContainerType>lambdaQuery()
                .eq(ContainerType::getContainerCode, request.containerCode()));
        if (existing != null) {
            throw new BusinessException("器具类型编码已存在");
        }

        ContainerType type = new ContainerType();
        type.setContainerCode(request.containerCode());
        type.setContainerName(request.containerName());
        type.setCapacityQty(request.capacityQty());
        type.setStatus(ENABLED);
        containerTypeMapper.insert(type);
        return type;
    }

    @Transactional
    public ContainerType update(Long id, ContainerTypeRequest request) {
        ContainerType type = requireContainerType(id);

        ContainerType existing = containerTypeMapper.selectOne(Wrappers.<ContainerType>lambdaQuery()
                .eq(ContainerType::getContainerCode, request.containerCode())
                .ne(ContainerType::getId, id));
        if (existing != null) {
            throw new BusinessException("器具类型编码已存在");
        }

        type.setContainerCode(request.containerCode());
        type.setContainerName(request.containerName());
        type.setCapacityQty(request.capacityQty());
        containerTypeMapper.updateById(type);
        return type;
    }

    @Transactional
    public void updateStatus(Long id, String status) {
        ContainerType type = requireContainerType(id);
        type.setStatus(status);
        containerTypeMapper.updateById(type);
    }

    public ContainerType requireContainerType(Long id) {
        ContainerType type = containerTypeMapper.selectById(id);
        if (type == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "器具类型不存在");
        }
        return type;
    }
}
