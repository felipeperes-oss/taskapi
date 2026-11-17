package br.edu.ifsp.web.servlet;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.Strictness;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;

public final class EntradaJson {
    private static final Gson GSON = new GsonBuilder().setStrictness(Strictness.STRICT).create();

    private EntradaJson() {
    }

    public static JsonObject lerObjeto(HttpServletRequest requisicao) throws IOException {
        JsonObject dados = GSON.fromJson(requisicao.getReader(), JsonObject.class);
        if (dados == null) {
            throw new IllegalArgumentException("Informe um objeto JSON.");
        }
        return dados;
    }

    public static String lerTexto(JsonObject dados, String campo) {
        JsonElement valor = dados.get(campo);
        if (valor == null || valor.isJsonNull()) {
            return null;
        }
        if (!valor.isJsonPrimitive() || !valor.getAsJsonPrimitive().isString()) {
            throw new IllegalArgumentException("O campo '" + campo + "' deve ser texto.");
        }
        return valor.getAsString();
    }
}
