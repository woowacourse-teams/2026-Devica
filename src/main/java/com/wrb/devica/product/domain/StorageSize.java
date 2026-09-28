package com.wrb.devica.product.domain;

/**
 * 저장 공간은 판매 표기를 따른다. 1024GB 는 1TB 로 팔린다.
 */
public final class StorageSize {

    private static final int GB_PER_TB = 1024;

    private StorageSize() {
    }

    public static String display(int gb) {
        if (gb >= GB_PER_TB && gb % GB_PER_TB == 0) {
            return gb / GB_PER_TB + "TB";
        }
        return gb + "GB";
    }
}
