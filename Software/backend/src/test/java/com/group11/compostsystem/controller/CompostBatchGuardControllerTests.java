package com.group11.compostsystem.controller;

import com.group11.compostsystem.dto.*;
import com.group11.compostsystem.service.*;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.junit.jupiter.api.Assertions.*;

class CompostBatchGuardControllerTests {
    @Test
    void activeBatchConflictsReturn409ForAllActivationPaths() {
        AuthService auth = mock(AuthService.class);
        CompostBatchService batches = mock(CompostBatchService.class);
        when(auth.getCurrentSession("test")).thenReturn(new AuthResult(
                new UserResponse(7L, "Operator", "test@example.com", "OPERATOR"), "test", null));
        var conflict = new CompostBatchService.ActiveBatchConflictException("An ongoing batch exists.");
        when(batches.createBatch(any(), eq(7))).thenThrow(conflict);
        when(batches.setActiveBatch(2)).thenThrow(conflict);
        when(batches.updateBatchStatus(eq(2), any())).thenThrow(conflict);
        var controller = new CompostBatchController(batches, auth);
        assertEquals(409, controller.createBatch(new CompostBatchRequest(), "Bearer test").getStatusCode().value());
        assertEquals(409, controller.setActiveBatch(2, "Bearer test").getStatusCode().value());
        assertEquals(409, controller.updateBatchStatus(2, new CompostBatchStatusRequest(), "Bearer test").getStatusCode().value());
    }
}
