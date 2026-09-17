package uz.mirmaxsudov.chatclonebackend.controller.tus;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import uz.mirmaxsudov.chatclonebackend.config.minio.TusProperties;
import uz.mirmaxsudov.chatclonebackend.service.tus.UploadService;

import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TusControllerTest {
    private final UploadService uploadService = mock(UploadService.class);
    private final TusProperties tusProperties = new TusProperties();
    private final MockMvc mockMvc = MockMvcBuilders
            .standaloneSetup(new TusController(uploadService, tusProperties))
            .build();

    @Test
    void optionsIsAvailableUnderVersionedApiPath() throws Exception {
        mockMvc.perform(options("/api/v1/files"))
                .andExpect(status().isNoContent())
                .andExpect(header().string("Tus-Resumable", "1.0.0"))
                .andExpect(header().string("Tus-Version", "1.0.0"))
                .andExpect(header().string("Tus-Extension", "creation,termination"));
    }
}
