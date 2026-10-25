package com.example.lifedots.wallpaper

import org.junit.Assert.*
import org.junit.Test

class LockWallpaperOwnershipTest {
    @Test fun `unrecorded or replaced wallpapers are never owned`() {
        assertFalse(ownsLockWallpaper(-1, 42))
        assertFalse(ownsLockWallpaper(0, 0))
        assertFalse(ownsLockWallpaper(42, -1))
        assertFalse(ownsLockWallpaper(42, 43))
        assertTrue(ownsLockWallpaper(42, 42))
    }
}
