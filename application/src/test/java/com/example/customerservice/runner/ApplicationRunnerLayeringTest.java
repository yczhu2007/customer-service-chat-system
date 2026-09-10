package com.example.customerservice.runner;

import com.example.customerservice.service.ChatAgentOperations;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class ApplicationRunnerLayeringTest {

    @Test
    void runnersDoNotDependDirectlyOnDataAccessTypes() {
        assertNoDataAccessFields(AdminBootstrapRunner.class);
        assertNoDataAccessFields(VipAgentSkillCacheInitializer.class);
    }

    @Test
    void vipInitializerDelegatesCacheRebuild() throws Exception {
        ChatAgentOperations operations = mock(ChatAgentOperations.class);

        new VipAgentSkillCacheInitializer(operations).run(null);

        verify(operations).rebuildVipSkillCache();
    }

    private void assertNoDataAccessFields(Class<?> type) {
        boolean hasDataAccessField = Arrays.stream(type.getDeclaredFields())
                .map(field -> field.getType().getSimpleName())
                .anyMatch(name -> name.endsWith("Mapper") || name.endsWith("Repository"));
        assertFalse(hasDataAccessField, type.getSimpleName() + " must delegate to a business service");
    }
}
