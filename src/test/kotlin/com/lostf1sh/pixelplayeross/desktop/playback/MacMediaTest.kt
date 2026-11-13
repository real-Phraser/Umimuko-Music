package com.lostf1sh.pixelplayeross.desktop.playback

import com.sun.jna.Callback
import com.sun.jna.NativeLibrary
import com.sun.jna.Pointer
import kotlin.test.Test
import kotlin.test.assertNotNull

class MacMediaTest {
    interface ObjcCallback : Callback {
        fun invoke(self: Pointer, cmd: Pointer, event: Pointer): Long
    }

    private val objc = NativeLibrary.getInstance("objc")
    private val getClass = objc.getFunction("objc_getClass")
    private val selRegister = objc.getFunction("sel_registerName")
    private val msgSend = objc.getFunction("objc_msgSend")

    private fun cls(name: String): Pointer = requireNotNull(getClass.invokePointer(arrayOf(name))) { "Missing class $name" }
    private fun sel(name: String): Pointer = requireNotNull(selRegister.invokePointer(arrayOf(name))) { "Missing sel $name" }
    private fun nsString(str: String): Pointer {
        val nsStrCls = cls("NSString")
        return msgSend.invokePointer(arrayOf(nsStrCls, sel("stringWithUTF8String:"), str))
    }

    @Test
    fun testMacMediaCenterFull() {
        if (!System.getProperty("os.name", "").lowercase().contains("mac")) return
        NativeLibrary.getInstance("/System/Library/Frameworks/MediaPlayer.framework/MediaPlayer")
        NativeLibrary.getInstance("/System/Library/Frameworks/AppKit.framework/AppKit")

        val mpCenterCls = cls("MPNowPlayingInfoCenter")
        val defaultCenter = msgSend.invokePointer(arrayOf(mpCenterCls, sel("defaultCenter")))
        assertNotNull(defaultCenter)

        val dictCls = cls("NSMutableDictionary")
        val dict = msgSend.invokePointer(arrayOf(dictCls, sel("dictionary")))
        assertNotNull(dict)

        val titleKey = nsString("title")
        val titleVal = nsString("Test Song")
        msgSend.invokeVoid(arrayOf(dict, sel("setObject:forKey:"), titleVal, titleKey))

        msgSend.invokeVoid(arrayOf(defaultCenter, sel("setNowPlayingInfo:"), dict))
        println("SUCCESS: setNowPlayingInfo succeeded!")

        // Clean up
        msgSend.invokeVoid(arrayOf(defaultCenter, sel("setNowPlayingInfo:"), Pointer.NULL))
    }
}
