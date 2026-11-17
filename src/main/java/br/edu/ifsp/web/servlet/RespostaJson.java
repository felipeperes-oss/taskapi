package br.edu.ifsp.web.servlet;

import com.google.gson.Gson;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;

public final class RespostaJson {
    private static final Gson GSON = new Gson();

    private RespostaJson() {
    }

    public static void enviar(HttpServletResponse resposta, int status, Object corpo) throws IOException {
        String json = GSON.toJson(corpo);
        resposta.setStatus(status);
        resposta.setContentType("application/json");
        resposta.setCharacterEncoding("UTF-8");
        resposta.getWriter().write(json);
    }

    public static void erro(HttpServletResponse resposta, int status, String mensagem) throws IOException {
        enviar(resposta, status, Map.of("erro", mensagem));
    }
}
