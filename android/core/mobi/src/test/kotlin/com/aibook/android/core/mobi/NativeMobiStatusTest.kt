package com.aibook.android.core.mobi

import kotlin.test.Test
import kotlin.test.assertEquals

class NativeMobiStatusTest {

    @Test
    fun nativeStatusMapsDrmToDomainError() {
        assertEquals(MobiParseError.DRM_PROTECTED, NativeMobiStatus.toError(NativeMobiStatus.DRM))
    }

    @Test
    fun unknownNativeStatusMapsToParseFailure() {
        assertEquals(MobiParseError.PARSE_FAILED, NativeMobiStatus.toError(999))
    }
}
