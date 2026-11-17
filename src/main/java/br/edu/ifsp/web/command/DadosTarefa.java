package br.edu.ifsp.web.command;

import br.edu.ifsp.web.servlet.EntradaJson;
import com.google.gson.JsonObject;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;

record DadosTarefa(String titulo, String descricao) {
    static DadosTarefa ler(HttpServletRequest requisicao) throws IOException {
        JsonObject dados = EntradaJson.lerObjeto(requisicao);
        return new DadosTarefa(EntradaJson.lerTexto(dados, "titulo"), EntradaJson.lerTexto(dados, "descricao"));
    }
}
