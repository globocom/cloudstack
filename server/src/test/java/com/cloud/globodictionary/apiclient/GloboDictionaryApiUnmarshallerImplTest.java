package com.cloud.globodictionary.apiclient;

import com.cloud.globodictionary.GloboDictionaryEntity;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Spy;

import java.util.List;

import static org.junit.Assert.assertEquals;

public class GloboDictionaryApiUnmarshallerImplTest {

    @Spy
    private GloboDictionaryApiUnmarshaller unmarshaller;


    @Before
    public void setUp() throws Exception {
        unmarshaller = new GloboDictionaryApiUnmarshallerImpl();
    }

    @Test
    public void testUnmarshalNonEmpty() throws Exception {
        List<GloboDictionaryEntity> result = unmarshaller.unmarshal(getBusinessServiceJSON());
        assertEquals(1, result.size(), 1);
        assertEquals("Assinatura - Vendas",result.get(0).getName());
        assertEquals("Ativo", result.get(0).getStatus());
    }

    @Test
    public void testUnmarshalEmpty() throws Exception {
        List<GloboDictionaryEntity> result = unmarshaller.unmarshal(getBusinessServiceJSON());
        assertEquals(0, result.size(), 1);
    }

    private String getBusinessServiceEmptyJSON() {
        return "[]";
    }

    private String getBusinessServiceJSON() {
        return "[{\"macro_servico\": \"Assinatura\", \"detalhe_servico\": \"Vendas\", \"nome\": \"Assinatura - Vendas\", \"descricao\": \"infraestrutura para o sistema de vendas\", \"driver_custeio\": \"N\\u00famero de Assinantes\", \"status\": \"Ativo\", \"coorporativo\": true, \"servico_negocio_armazenamento_id\": \"31cf5f75db9f43409fc15458dc96198b\", \"id_service_now\": \"31cf5f75db9f43409fc15458dc96198b\"}]";
    }

}
