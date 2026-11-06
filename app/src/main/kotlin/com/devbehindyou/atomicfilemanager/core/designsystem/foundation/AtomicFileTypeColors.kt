package com.devbehindyou.atomicfilemanager.core.designsystem.foundation

/** Stable file identities; shape and the stored file name remain available alongside colour. */
enum class AtomicFileType {
    FOLDER,
    IMAGE,
    VIDEO,
    AUDIO,
    PDF,
    APK,
    ARCHIVE,
    CODE,
    TEXT,
    DOCUMENT,
    SPREADSHEET,
    PRESENTATION,
    EBOOK,
    FONT,
    GENERIC,
}

/**
 * File-type icon colours are independent of the wallpaper accent. Dark themes use lighter tones;
 * light themes use deeper tones, with readable contrast against every Atomic file surface.
 */
object AtomicFileTypeColors {
    fun icon(
        type: AtomicFileType,
        isDark: Boolean,
    ): Int = if (isDark) dark.getValue(type) else light.getValue(type)

    private val light =
        mapOf(
            AtomicFileType.FOLDER to 0xFF805900.toInt(),
            AtomicFileType.IMAGE to 0xFF006B64.toInt(),
            AtomicFileType.VIDEO to 0xFF6B3DA6.toInt(),
            AtomicFileType.AUDIO to 0xFF924065.toInt(),
            AtomicFileType.PDF to 0xFFB3261E.toInt(),
            AtomicFileType.APK to 0xFF31652B.toInt(),
            AtomicFileType.ARCHIVE to 0xFF914900.toInt(),
            AtomicFileType.CODE to 0xFF3355A4.toInt(),
            AtomicFileType.TEXT to 0xFF00677F.toInt(),
            AtomicFileType.DOCUMENT to 0xFF315D80.toInt(),
            AtomicFileType.SPREADSHEET to 0xFF006B3C.toInt(),
            AtomicFileType.PRESENTATION to 0xFF9B3D21.toInt(),
            AtomicFileType.EBOOK to 0xFF755100.toInt(),
            AtomicFileType.FONT to 0xFF714671.toInt(),
            AtomicFileType.GENERIC to 0xFF4A4D55.toInt(),
        )
    private val dark =
        mapOf(
            AtomicFileType.FOLDER to 0xFFE8C568.toInt(),
            AtomicFileType.IMAGE to 0xFF75D7C8.toInt(),
            AtomicFileType.VIDEO to 0xFFC4A3F4.toInt(),
            AtomicFileType.AUDIO to 0xFFF0A6CC.toInt(),
            AtomicFileType.PDF to 0xFFFFABA3.toInt(),
            AtomicFileType.APK to 0xFFA1D987.toInt(),
            AtomicFileType.ARCHIVE to 0xFFF7BA77.toInt(),
            AtomicFileType.CODE to 0xFFA8BCFF.toInt(),
            AtomicFileType.TEXT to 0xFF89D3EC.toInt(),
            AtomicFileType.DOCUMENT to 0xFFA1C9EE.toInt(),
            AtomicFileType.SPREADSHEET to 0xFF78DCA7.toInt(),
            AtomicFileType.PRESENTATION to 0xFFFFB096.toInt(),
            AtomicFileType.EBOOK to 0xFFE2C185.toInt(),
            AtomicFileType.FONT to 0xFFD4B2E0.toInt(),
            AtomicFileType.GENERIC to 0xFFC5C7CF.toInt(),
        )
}
