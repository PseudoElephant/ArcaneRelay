package com.pseudoelephant.arcanerelay.util;

public class BlockFlags {
    public static int BREAK_BLOCK_VFX = 1 << 2; // 4
    public static int BREAK_BLOCK_SFX = 1 << 10; // 1024

    private int value;

    public BlockFlags(int value) {
        this.value = value;
    }

    public BlockFlags() {

    }

    public BlockFlags add(int flag) {
        this.value = this.value | flag;

        return this;
    }

    public BlockFlags remove(int flag) {
        this.value &= ~flag;
        return this;
    }

    public boolean has(int flag) {
        return (this.value & flag) == flag;
    }

    public int getValue() {
        return this.value;
    }
}
