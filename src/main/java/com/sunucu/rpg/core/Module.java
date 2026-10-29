package com.sunucu.rpg.core;

/** Bir sistem: dinleyicilerini ve komutlarını kendi içinde kaydeder. */
public interface Module {

    void enable();

    default void disable() {
    }
}
