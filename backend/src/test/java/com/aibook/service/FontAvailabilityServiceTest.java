package com.aibook.service;

import com.aibook.model.entity.FontAsset;
import com.aibook.repository.FontAssetRepository;
import com.aibook.repository.FontScanDirectoryRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FontAvailabilityServiceTest {

    @Test
    void persistsUnavailableStateReportedByBrowser() {
        FontAssetRepository assets = mock(FontAssetRepository.class);
        FontAsset asset = FontAsset.builder().id(7L).available(true).build();
        when(assets.findById(7L)).thenReturn(Optional.of(asset));
        FontService service = new FontService(
                assets,
                mock(FontScanDirectoryRepository.class),
                mock(FontFileInspector.class));

        service.markUnavailable(7L);

        assertFalse(asset.getAvailable());
        verify(assets).save(asset);
    }
}
