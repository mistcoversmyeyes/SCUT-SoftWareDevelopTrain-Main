import java.util.HashMap;
import java.util.Scanner;

public class substring {
    public int length(String s){
        if(s==null || s.length() ==0){
            return 0;
        }
        HashMap<Character,Integer> map=new HashMap<>();
        int length=0;
        int left = 0;
        for (int right = 0;right <s.length();right++) {
            char a = s.charAt(right);
            if (map.containsKey(a) && map.get(a) >= left) {
                left = map.get(a) + 1;
            }
            map.put(a, right);
            length = Math.max(length, right - left + 1);
        }
        return length;
        }
        public static void main(String[] args){
        substring s=new substring();
        System.out.println(s.length("abcabcbb"));
        System.out.println(s.length("bbbbb"));
        System.out.println(s.length("pwwkew"));
        }
    }

