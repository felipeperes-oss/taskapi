package br.edu.ifsp.web.servlet;

import br.edu.ifsp.web.dao.ConnectionFactory;
import br.edu.ifsp.web.filter.FiltroAutenticacao;
import br.edu.ifsp.web.model.Tarefa;
import br.edu.ifsp.web.model.Usuario;
import br.edu.ifsp.web.service.TarefaService;
import br.edu.ifsp.web.service.TarefaServiceImpl;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import jakarta.servlet.ServletConfig;
import jakarta.servlet.ServletContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.BufferedReader;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import org.h2.tools.RunScript;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class FrontControllerTest {
    private final Usuario ana = new Usuario(1, "Ana", "ana@taskapi.test");
    private final Usuario bruno = new Usuario(2, "Bruno", "bruno@taskapi.test");
    private final List<Throwable> errosRegistrados = new ArrayList<>();
    private ConnectionFactory connectionFactory;
    private TarefaService tarefas;
    private FrontController controller;

    @BeforeEach
    void preparar() throws Exception {
        String url = "jdbc:h2:mem:" + UUID.randomUUID()
                + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";
        connectionFactory = new ConnectionFactory(url, "sa", "");
        String schema = Files.readString(Path.of("schema.sql"))
                .replace("CREATE DATABASE IF NOT EXISTS taskdb;", "")
                .replace("USE taskdb;", "");
        try (Connection conexao = connectionFactory.getConnection()) {
            RunScript.execute(conexao, new StringReader(schema));
        }
        tarefas = new TarefaServiceImpl(connectionFactory);
        controller = criarController(tarefas);
    }

    @AfterEach
    void encerrar() throws SQLException {
        try (Connection conexao = connectionFactory.getConnection();
             var stmt = conexao.createStatement()) {
            stmt.execute("SHUTDOWN");
        }
    }

    @Test
    void listaAsTarefasDoUsuarioEmJson() throws Exception {
        Resposta respostaAna = executar("GET", null, "", ana);
        assertEquals(200, respostaAna.status);
        verificarJson(respostaAna);
        var listaAna = JsonParser.parseString(respostaAna.corpo.toString()).getAsJsonArray();
        assertEquals(5, listaAna.size());
        listaAna.forEach(item -> assertEquals(ana.getId(),
                item.getAsJsonObject().getAsJsonObject("usuario").get("id").getAsInt()));
        Resposta respostaBruno = executar("GET", "/", "", bruno);
        assertEquals(200, respostaBruno.status);
        assertEquals(3, JsonParser.parseString(respostaBruno.corpo.toString()).getAsJsonArray().size());
    }

    @Test
    void buscaUmaTarefaPeloId() throws Exception {
        Tarefa original = tarefas.listar(ana).getFirst();
        Resposta resposta = executar("GET", "/" + original.getId(), "", ana);
        assertEquals(200, resposta.status);
        verificarJson(resposta);
        assertEquals(original.getId(), resposta.json().get("id").getAsInt());
        assertEquals(original.getTitulo(), resposta.json().get("titulo").getAsString());
        assertEquals(Set.of("id", "nome", "email"), resposta.json().getAsJsonObject("usuario").keySet());
    }

    @Test
    void postRetornaATarefaComIdGeradoEIgnoraDonoEConclusaoEnviados() throws Exception {
        String corpo = "{\"titulo\":\"Criada pela API\",\"descricao\":\"Descrição com acento\","
                + "\"id\":99999,\"concluida\":true,\"usuario\":{\"id\":2}}";
        Resposta resposta = executar("POST", null, corpo, ana);
        assertEquals(201, resposta.status);
        verificarJson(resposta);
        int id = resposta.json().get("id").getAsInt();
        assertTrue(id > 0);
        assertNotEquals(99999, id);
        assertEquals("Criada pela API", resposta.json().get("titulo").getAsString());
        assertEquals("Descrição com acento", resposta.json().get("descricao").getAsString());
        assertFalse(resposta.json().get("concluida").getAsBoolean());
        assertEquals(ana.getId(), resposta.json().getAsJsonObject("usuario").get("id").getAsInt());
        assertEquals("Criada pela API", tarefas.buscar(ana, id).getTitulo());
        assertEquals(3, tarefas.listar(bruno).size());
    }

    @Test
    void putRetornaATarefaAtualizadaEPreservaDonoEConclusao() throws Exception {
        Tarefa original = tarefas.listar(ana).stream().filter(Tarefa::isConcluida).findFirst().orElseThrow();
        Resposta resposta = executar("PUT", "/" + original.getId(),
                "{\"titulo\":\"Alterada\",\"descricao\":\"Nova descrição\",\"concluida\":false,\"usuario\":{\"id\":2}}", ana);
        assertEquals(200, resposta.status);
        verificarJson(resposta);
        assertEquals(original.getId(), resposta.json().get("id").getAsInt());
        assertEquals("Alterada", resposta.json().get("titulo").getAsString());
        assertTrue(resposta.json().get("concluida").getAsBoolean());
        Tarefa persistida = tarefas.buscar(ana, original.getId());
        assertEquals("Nova descrição", persistida.getDescricao());
        assertTrue(persistida.isConcluida());
        assertEquals(ana.getId(), persistida.getUsuario().getId());
    }

    @Test
    void patchConcluiSemCorpoERepeticaoRetornaConflitoEmJson() throws Exception {
        Tarefa tarefa = tarefas.criar(ana, "Concluir pela API", null);
        Resposta primeira = executar("PATCH", "/" + tarefa.getId(), "", ana);
        assertEquals(204, primeira.status);
        assertEquals("", primeira.corpo.toString());
        assertTrue(tarefas.buscar(ana, tarefa.getId()).isConcluida());
        verificarErro(executar("PATCH", "/" + tarefa.getId(), "", ana), 409);
    }

    @Test
    void deleteExcluiSemCorpo() throws Exception {
        Tarefa tarefa = tarefas.criar(ana, "Excluir pela API", null);
        Resposta resposta = executar("DELETE", "/" + tarefa.getId(), "", ana);
        assertEquals(204, resposta.status);
        assertEquals("", resposta.corpo.toString());
        verificarErro(executar("GET", "/" + tarefa.getId(), "", ana), 404);
        verificarErro(executar("DELETE", "/" + tarefa.getId(), "", ana), 404);
    }

    @ParameterizedTest
    @ValueSource(strings = {"GET", "PUT", "PATCH", "DELETE"})
    void tarefaAlheiaEInexistenteRetornam404SemAlterarDados(String metodo) throws Exception {
        Tarefa alheia = tarefas.listar(bruno).getFirst();
        String corpo = "{\"titulo\":\"Tentativa de alteração\"}";
        Resposta respostaAlheia = executar(metodo, "/" + alheia.getId(), corpo, ana);
        Resposta respostaAusente = executar(metodo, "/2147483647", corpo, ana);
        verificarErro(respostaAlheia, 404);
        verificarErro(respostaAusente, 404);
        assertEquals(respostaAusente.json(), respostaAlheia.json());
        Tarefa preservada = tarefas.buscar(bruno, alheia.getId());
        assertEquals(alheia.getTitulo(), preservada.getTitulo());
        assertEquals(alheia.getDescricao(), preservada.getDescricao());
        assertEquals(alheia.isConcluida(), preservada.isConcluida());
        assertEquals(3, tarefas.listar(bruno).size());
    }

    @ParameterizedTest
    @MethodSource("corposInvalidos")
    void postEPutRecusamCorposInvalidosSemAlteracaoParcial(String metodo, String corpo) throws Exception {
        Tarefa original = tarefas.listar(ana).getFirst();
        String caminho = metodo.equals("POST") ? null : "/" + original.getId();
        verificarErro(executar(metodo, caminho, corpo, ana), 400);
        assertEquals(5, tarefas.listar(ana).size());
        assertEquals(original.getTitulo(), tarefas.buscar(ana, original.getId()).getTitulo());
        assertEquals(original.getDescricao(), tarefas.buscar(ana, original.getId()).getDescricao());
        assertTrue(errosRegistrados.isEmpty());
    }

    static Stream<Arguments> corposInvalidos() {
        List<String> corpos = List.of("", "null", "{", "[]", "1", "true", "{}",
                "{\"titulo\":null}", "{\"titulo\":\" \"}",
                "{\"titulo\":123}", "{\"titulo\":true}", "{\"titulo\":{}}", "{\"titulo\":[]}",
                "{\"titulo\":\"Titulo\",\"descricao\":[]}",
                "{titulo: 'Sem aspas duplas'}", "{\"titulo\":\"Titulo\"} lixo");
        return Stream.of("POST", "PUT").flatMap(metodo -> corpos.stream().map(corpo -> Arguments.of(metodo, corpo)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/abc", "/-1", "/0", "/2147483648", "/1/extra"})
    void idInvalidoRetorna400EmJson(String caminho) throws Exception {
        verificarErro(executar("GET", caminho, "", ana), 400);
    }

    @ParameterizedTest
    @CsvSource({"PUT, COLECAO", "PATCH, COLECAO", "DELETE, COLECAO", "OPTIONS, COLECAO",
            "HEAD, COLECAO", "POST, ITEM", "TRACE, ITEM"})
    void metodoNaoPermitidoRetornaJsonEHeaderAllow(String metodo, String rota) throws Exception {
        String caminho = rota.equals("COLECAO") ? null : "/1";
        Resposta resposta = executar(metodo, caminho, "{}", ana);
        verificarErro(resposta, 405);
        assertEquals(rota.equals("COLECAO") ? "GET, POST" : "GET, PUT, PATCH, DELETE",
                resposta.headers.get("Allow"));
    }

    @ParameterizedTest
    @MethodSource("rotas")
    void semUsuarioRetorna401SemCriarSessao(String metodo, String caminho) throws Exception {
        verificarErro(executar(metodo, caminho, "{\"titulo\":\"Titulo\"}", null), 401);
        assertEquals(5, tarefas.listar(ana).size());
    }

    @Test
    void sessaoSemUsuarioValidoRetorna401() throws Exception {
        verificarErro(executar("GET", null, "", "usuario-invalido"), 401);
    }

    @ParameterizedTest
    @MethodSource("rotas")
    void falhaInternaRetorna500SemExporDetalhes(String metodo, String caminho) throws Exception {
        ConnectionFactory factory = new ConnectionFactory() {
            @Override
            public Connection getConnection() throws SQLException {
                throw new SQLException("Detalhes privados da conexão.");
            }
        };
        controller = criarController(new TarefaServiceImpl(factory));
        Resposta resposta = executar(metodo, caminho, "{\"titulo\":\"Titulo\"}", ana);
        verificarErro(resposta, 500);
        assertEquals("Erro interno do servidor.", resposta.json().get("erro").getAsString());
        assertFalse(resposta.corpo.toString().contains("privados"));
        assertEquals(1, errosRegistrados.size());
    }

    static Stream<Arguments> rotas() {
        return Stream.of(Arguments.of("GET", null), Arguments.of("GET", "/1"),
                Arguments.of("POST", null), Arguments.of("PUT", "/1"),
                Arguments.of("PATCH", "/1"), Arguments.of("DELETE", "/1"));
    }

    private FrontController criarController(TarefaService service) throws Exception {
        ServletContext contexto = (ServletContext) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[] {ServletContext.class}, (proxy, metodo, argumentos) -> {
                    if (metodo.getName().equals("getAttribute")) {
                        return service;
                    }
                    if (metodo.getName().equals("log") && argumentos.length == 2) {
                        errosRegistrados.add((Throwable) argumentos[1]);
                    }
                    return null;
                });
        ServletConfig config = (ServletConfig) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[] {ServletConfig.class}, (proxy, metodo, argumentos) -> {
                    return metodo.getName().equals("getServletContext") ? contexto : null;
                });
        FrontController servlet = new FrontController();
        servlet.init(config);
        return servlet;
    }

    private Resposta executar(String metodo, String caminho, String corpo, Object usuario) throws Exception {
        Resposta resposta = new Resposta();
        Map<String, Object> atributos = new HashMap<>();
        HttpSession sessao = usuario == null ? null : (HttpSession) Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class<?>[] {HttpSession.class},
                (proxy, operacao, argumentos) -> operacao.getName().equals("getAttribute") ? usuario : null);
        HttpServletRequest requisicao = (HttpServletRequest) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[] {HttpServletRequest.class}, (proxy, operacao, argumentos) -> {
                    return switch (operacao.getName()) {
                        case "getMethod" -> metodo;
                        case "getPathInfo" -> caminho;
                        case "getReader" -> new BufferedReader(new StringReader(corpo));
                        case "getSession" -> {
                            assertNotNull(argumentos);
                            assertEquals(Boolean.FALSE, argumentos[0]);
                            yield sessao;
                        }
                        case "getAttribute" -> atributos.get(argumentos[0]);
                        case "setAttribute" -> {
                            atributos.put((String) argumentos[0], argumentos[1]);
                            yield null;
                        }
                        default -> null;
                    };
                });
        HttpServletResponse httpResposta = (HttpServletResponse) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[] {HttpServletResponse.class}, (proxy, operacao, argumentos) -> {
                    switch (operacao.getName()) {
                        case "setStatus" -> resposta.status = (Integer) argumentos[0];
                        case "setContentType" -> resposta.tipo = (String) argumentos[0];
                        case "setCharacterEncoding" -> resposta.encoding = (String) argumentos[0];
                        case "setHeader" -> resposta.headers.put((String) argumentos[0], (String) argumentos[1]);
                        case "getWriter" -> {
                            return new PrintWriter(resposta.corpo);
                        }
                        case "sendError" -> fail("Erros devem ser escritos como JSON.");
                    }
                    return null;
                });
        new FiltroAutenticacao().doFilter(requisicao, httpResposta,
                (req, resp) -> controller.service((HttpServletRequest) req, (HttpServletResponse) resp));
        return resposta;
    }

    private void verificarJson(Resposta resposta) {
        assertEquals("application/json", resposta.tipo);
        assertEquals("UTF-8", resposta.encoding);
        assertDoesNotThrow(() -> JsonParser.parseString(resposta.corpo.toString()));
    }

    private void verificarErro(Resposta resposta, int status) {
        assertEquals(status, resposta.status);
        verificarJson(resposta);
        assertEquals(Set.of("erro"), resposta.json().keySet());
        assertTrue(resposta.json().get("erro").isJsonPrimitive());
        assertFalse(resposta.json().get("erro").getAsString().isBlank());
    }

    private static class Resposta {
        private int status = 200;
        private String tipo;
        private String encoding;
        private final StringWriter corpo = new StringWriter();
        private final Map<String, String> headers = new HashMap<>();

        JsonObject json() {
            return JsonParser.parseString(corpo.toString()).getAsJsonObject();
        }
    }
}
