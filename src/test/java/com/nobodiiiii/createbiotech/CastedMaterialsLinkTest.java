package com.nobodiiiii.createbiotech;

import com.nobodiiiii.createbiotech.foundation.render.material.MaterialRenderingModule;
import com.nobodiiiii.createbiotech.foundation.render.material.CastedMaterialsApi;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class CastedMaterialsLinkTest {
    @Test
    void loadsTheMaterialSubsystemFromTheRenderingNamespace() throws Exception {
        String module = "com.nobodiiiii.createbiotech.foundation.render.material.";
        ClassLoader loader = getClass().getClassLoader();
        for (String entry : new String[]{"MaterialRenderingModule", "CastedMaterialsApi",
                "client.CastedMaterialsClient", "mapping.UvPlanCompiler", "mapping.UvMapping"}) {
            assertNotNull(Class.forName(module + entry, false, loader));
        }
    }

    @Test
    void linksTheInternalMaterialApiWithoutAnExternalLibrary() {
        assertEquals("casted_materials", MaterialRenderingModule.DATA_NAMESPACE);
        assertNotNull(CastedMaterialsApi.class);
    }
}
