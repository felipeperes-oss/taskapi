package br.edu.ifsp.web.servlet;

import br.edu.ifsp.web.dao.ConnectionFactory;
import br.edu.ifsp.web.listener.SessoesListener;
import br.edu.ifsp.web.model.Usuario;
import br.edu.ifsp.web.service.AutenticacaoService;
import br.edu.ifsp.web.service.AutenticacaoServiceImpl;
import br.edu.ifsp.web.service.TarefaService;
import br.edu.ifsp.web.service.TarefaServiceImpl;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.StringReader;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.catalina.Context;
import org.apache.catalina.Session;
import org.apache.catalina.startup.ContextConfig;
import org.apache.catalina.startup.Tomcat;
import org.h2.tools.RunScript;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AuthControllerIntegrationTest {
    @TempDir
    static Path diretorio;

    private final AtomicBoolean falharConexao = new AtomicBoolean();
    private final AtomicInteger sessoesAtivas = new AtomicInteger();
    private final List<Navegador> navegadores = new ArrayList<>();
    private ConnectionFactory connectionFactory;
    private Tomcat tomcat;
    private Context contexto;
    private URI endereco;
    private Navegador navegador;

    @BeforeAll
    void iniciarServidor() throws Exception {
        String url = "jdbc:h2:mem:" + UUID.randomUUID()
                + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";
        connectionFactory = new ConnectionFactory(url, "sa", "") {
            @Override
            public Connection getConnection() throws SQLException {
                if (falharConexao.get()) {
                    throw new SQLException("Detalhes privados da conexão.");
                }
                return super.getConnection();
            }
        };
        String schema = Files.readString(Path.of("schema.sql"))
                .replace("CREATE DATABASE IF NOT EXISTS taskdb;", "")
                .replace("USE taskdb;", "");
        try (Connection conexao = connectionFactory.getConnection()) {
            RunScript.execute(conexao, new StringReader(schema));
        }
        AutenticacaoService autenticacao = new AutenticacaoServiceImpl(connectionFactory);
        TarefaService tarefas = new TarefaServiceImpl(connectionFactory);
        Path aplicacao = Files.createDirectories(diretorio.resolve("aplicacao"));
        Path webInf = Files.createDirectories(aplicacao.resolve("WEB-INF"));
        Files.copy(Path.of("src/main/webapp/WEB-INF/web.xml"), webInf.resolve("web.xml"));

        tomcat = new Tomcat();
        tomcat.setBaseDir(diretorio.resolve("tomcat").toString());
        tomcat.setPort(0);
        tomcat.setAddDefaultWebXmlToWebapp(false);
        tomcat.getConnector().setProperty("address", "127.0.0.1");
        ContextConfig configuracao = new ContextConfig();
        configuracao.setDefaultWebXml(tomcat.noDefaultWebXmlPath());
        contexto = tomcat.addWebapp(tomcat.getHost(), "", aplicacao.toString(), configuracao);
        contexto.setParentClassLoader(getClass().getClassLoader());
        contexto.setIgnoreAnnotations(true);
        contexto.addApplicationListener(SessoesListener.class.getName());
        contexto.addServletContainerInitializer((classes, servletContext) -> {
            servletContext.setAttribute("autenticacaoService", autenticacao);
            servletContext.setAttribute("tarefaService", tarefas);
            servletContext.setAttribute("sessoesAtivas", sessoesAtivas);
        }, Set.of());
        Tomcat.addServlet(contexto, "auth", new AuthController());
        contexto.addServletMappingDecoded("/auth/*", "auth");
        Tomcat.addServlet(contexto, "tarefas", new FrontController());
        contexto.addServletMappingDecoded("/tarefas/*", "tarefas");
        Tomcat.addServlet(contexto, "sessaoTeste", new HttpServlet() {
            @Override
            protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
                RespostaJson.enviar(resp, 200, Map.of("id", req.getSession(true).getId()));
            }
        });
        contexto.addServletMappingDecoded("/sessao-teste", "sessaoTeste");
        tomcat.start();
        assertTrue(contexto.getState().isAvailable());
        endereco = URI.create("http://127.0.0.1:" + tomcat.getConnector().getLocalPort());
    }

    @BeforeEach
    void prepararCliente() {
        falharConexao.set(false);
        for (Session sessao : contexto.getManager().findSessions()) {
            sessao.expire();
        }
        assertEquals(0, sessoesAtivas.get());
        navegador = novoNavegador();
    }

    @AfterEach
    void fecharClientes() {
        falharConexao.set(false);
        navegadores.forEach(cliente -> cliente.http().close());
        navegadores.clear();
    }

    @AfterAll
    void encerrarServidor() throws Exception {
        if (tomcat != null) {
            tomcat.stop();
            tomcat.destroy();
        }
        if (connectionFactory != null) {
            try (Connection conexao = connectionFactory.getConnection();
                 var stmt = conexao.createStatement()) {
                stmt.execute("SHUTDOWN");
            }
        }
    }

    @Test
    void loginCriaCookieESessaoSemCredencial() throws Exception {
        HttpResponse<String> resposta = login(navegador, "ana@taskapi.test", "AnaTeste123!");
        assertEquals(200, resposta.statusCode());
        verificarJson(resposta);
        assertEquals(Set.of("id", "nome", "email"), json(resposta).keySet());
        assertEquals("ana@taskapi.test", json(resposta).get("email").getAsString());
        assertFalse(resposta.body().contains("AnaTeste123!"));
        assertFalse(resposta.body().contains("pbkdf2"));
        assertTrue(resposta.headers().allValues("Set-Cookie").stream().anyMatch(cookie -> cookie.contains("HttpOnly")));
        Session sessao = contexto.getManager().findSession(idSessao(navegador));
        assertNotNull(sessao);
        assertEquals(1800, sessao.getMaxInactiveInterval());
        assertEquals(List.of("usuarioLogado"), Collections.list(sessao.getSession().getAttributeNames()));
        Usuario usuario = (Usuario) sessao.getSession().getAttribute("usuarioLogado");
        assertEquals(1, usuario.getId());
        assertEquals(Set.of("id", "nome", "email"),
                new com.google.gson.Gson().toJsonTree(usuario).getAsJsonObject().keySet());
        assertEquals(1, sessoesAtivas.get());
    }

    @Test
    void loginTrocaOIdentificadorMesmoQuandoJaExisteSessao() throws Exception {
        requisitar(navegador, "GET", "/sessao-teste", "");
        String anterior = idSessao(navegador);
        assertEquals(1, sessoesAtivas.get());
        assertEquals(200, login(navegador, "ana@taskapi.test", "AnaTeste123!").statusCode());
        String atual = idSessao(navegador);
        assertNotEquals(anterior, atual);
        assertNull(contexto.getManager().findSession(anterior));
        assertNotNull(contexto.getManager().findSession(atual));
        assertEquals(1, sessoesAtivas.get());
        assertEquals(200, login(navegador, "bruno@taskapi.test", "BrunoTeste123!").statusCode());
        assertNotEquals(atual, idSessao(navegador));
        assertEquals("bruno@taskapi.test", json(requisitar(navegador, "GET", "/auth/me", ""))
                .getAsJsonObject("usuario").get("email").getAsString());
        assertEquals(1, sessoesAtivas.get());
    }

    @Test
    void senhaErradaEEmailAusenteTemAMesmaMensagemESemCriarSessao() throws Exception {
        HttpResponse<String> senhaErrada = login(navegador, "ana@taskapi.test", "Errada");
        HttpResponse<String> emailAusente = login(navegador, "ausente@taskapi.test", "AnaTeste123!");
        verificarErro(senhaErrada, 401);
        verificarErro(emailAusente, 401);
        assertEquals(json(senhaErrada), json(emailAusente));
        assertTrue(senhaErrada.headers().allValues("Set-Cookie").isEmpty());
        assertTrue(emailAusente.headers().allValues("Set-Cookie").isEmpty());
        assertTrue(navegador.cookies().getCookieStore().getCookies().isEmpty());
        assertEquals(0, sessoesAtivas.get());
    }

    @Test
    void loginInvalidoNaoTrocaNemAutenticaSessaoExistente() throws Exception {
        requisitar(navegador, "GET", "/sessao-teste", "");
        String anterior = idSessao(navegador);
        verificarErro(login(navegador, "ana@taskapi.test", "Errada"), 401);
        assertEquals(anterior, idSessao(navegador));
        assertNull(contexto.getManager().findSession(anterior).getSession().getAttribute("usuarioLogado"));
        assertEquals(1, sessoesAtivas.get());
    }

    @Test
    void meSemSessaoRetorna401SemCriarCookie() throws Exception {
        HttpResponse<String> resposta = requisitar(navegador, "GET", "/auth/me", "");
        verificarErro(resposta, 401);
        assertTrue(resposta.headers().allValues("Set-Cookie").isEmpty());
        assertEquals(0, sessoesAtivas.get());
    }

    @Test
    void meMostraUsuarioEContagemRealEntreDoisClientes() throws Exception {
        Navegador segundo = novoNavegador();
        login(navegador, "ana@taskapi.test", "AnaTeste123!");
        login(segundo, "bruno@taskapi.test", "BrunoTeste123!");
        HttpResponse<String> resposta = requisitar(navegador, "GET", "/auth/me", "");
        assertEquals(200, resposta.statusCode());
        verificarJson(resposta);
        assertEquals(Set.of("usuario", "sessoesAtivas"), json(resposta).keySet());
        assertEquals("ana@taskapi.test", json(resposta).getAsJsonObject("usuario").get("email").getAsString());
        assertEquals(2, json(resposta).get("sessoesAtivas").getAsInt());
        assertEquals(204, requisitar(segundo, "POST", "/auth/logout", "").statusCode());
        assertEquals(1, json(requisitar(navegador, "GET", "/auth/me", "")).get("sessoesAtivas").getAsInt());
    }

    @Test
    void logoutInvalidaSessaoEImpedeAcessoAsTarefas() throws Exception {
        login(navegador, "ana@taskapi.test", "AnaTeste123!");
        String id = idSessao(navegador);
        HttpResponse<String> lista = requisitar(navegador, "GET", "/tarefas", "");
        assertEquals(200, lista.statusCode());
        assertEquals(5, JsonParser.parseString(lista.body()).getAsJsonArray().size());
        HttpResponse<String> logout = requisitar(navegador, "POST", "/auth/logout", "");
        assertEquals(204, logout.statusCode());
        assertEquals("", logout.body());
        assertNull(contexto.getManager().findSession(id));
        assertEquals(0, sessoesAtivas.get());
        verificarErro(requisitar(navegador, "GET", "/auth/me", ""), 401);
        verificarErro(requisitar(navegador, "GET", "/tarefas", ""), 401);
        assertEquals(204, requisitar(navegador, "POST", "/auth/logout", "").statusCode());
        assertEquals(0, sessoesAtivas.get());
    }

    @Test
    void logoutSemSessaoRetorna204SemCriarCookie() throws Exception {
        HttpResponse<String> resposta = requisitar(navegador, "POST", "/auth/logout", "");
        assertEquals(204, resposta.statusCode());
        assertEquals("", resposta.body());
        assertTrue(resposta.headers().allValues("Set-Cookie").isEmpty());
        assertEquals(0, sessoesAtivas.get());
    }

    @Test
    void sessaoExpiradaPerdeAcessoEDiminuiContagem() throws Exception {
        login(navegador, "ana@taskapi.test", "AnaTeste123!");
        Session sessao = contexto.getManager().findSession(idSessao(navegador));
        sessao.setMaxInactiveInterval(1);
        Thread.sleep(1500);
        verificarErro(requisitar(navegador, "GET", "/auth/me", ""), 401);
        assertEquals(0, sessoesAtivas.get());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "null", "{", "[]", "1", "true", "{email:'ana@taskapi.test'}",
            "{\"email\":123,\"senha\":\"AnaTeste123!\"}",
            "{\"email\":\"ana@taskapi.test\",\"senha\":false}",
            "{\"email\":\"ana@taskapi.test\",\"senha\":[]}"})
    void loginRecusaJsonInvalidoSemCriarSessao(String corpo) throws Exception {
        HttpResponse<String> resposta = requisitar(navegador, "POST", "/auth/login", corpo);
        verificarErro(resposta, 400);
        assertTrue(resposta.headers().allValues("Set-Cookie").isEmpty());
        assertEquals(0, sessoesAtivas.get());
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"email\":null,\"senha\":null}",
            "{\"email\":\"ana@taskapi.test\"}", "{\"senha\":\"AnaTeste123!\"}"})
    void loginRecusaCredenciaisAusentes(String corpo) throws Exception {
        verificarErro(requisitar(navegador, "POST", "/auth/login", corpo), 401);
        assertEquals(0, sessoesAtivas.get());
    }

    @ParameterizedTest
    @CsvSource({"GET, /auth/login, POST", "PUT, /auth/login, POST", "GET, /auth/logout, POST",
            "DELETE, /auth/logout, POST", "POST, /auth/me, GET", "PATCH, /auth/me, GET"})
    void metodoIncorretoRetorna405EmJson(String metodo, String caminho, String permitido) throws Exception {
        HttpResponse<String> resposta = requisitar(navegador, metodo, caminho, "{}");
        verificarErro(resposta, 405);
        assertEquals(permitido, resposta.headers().firstValue("Allow").orElseThrow());
        assertEquals(0, sessoesAtivas.get());
    }

    @Test
    void rotaDesconhecidaRetorna404EmJson() throws Exception {
        verificarErro(requisitar(navegador, "GET", "/auth/desconhecida", ""), 404);
    }

    @Test
    void falhaInternaNoLoginNaoCriaSessaoNemExpoeDetalhes() throws Exception {
        falharConexao.set(true);
        HttpResponse<String> resposta = login(navegador, "ana@taskapi.test", "AnaTeste123!");
        verificarErro(resposta, 500);
        assertEquals("Erro interno do servidor.", json(resposta).get("erro").getAsString());
        assertFalse(resposta.body().contains("privados"));
        assertTrue(resposta.headers().allValues("Set-Cookie").isEmpty());
        assertEquals(0, sessoesAtivas.get());
    }

    private Navegador novoNavegador() {
        CookieManager cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        HttpClient http = HttpClient.newBuilder().cookieHandler(cookies)
                .connectTimeout(Duration.ofSeconds(5)).build();
        Navegador cliente = new Navegador(http, cookies);
        navegadores.add(cliente);
        return cliente;
    }

    private HttpResponse<String> login(Navegador cliente, String email, String senha) throws Exception {
        JsonObject corpo = new JsonObject();
        corpo.addProperty("email", email);
        corpo.addProperty("senha", senha);
        return requisitar(cliente, "POST", "/auth/login", corpo.toString());
    }

    private HttpResponse<String> requisitar(Navegador cliente, String metodo, String caminho, String corpo) throws Exception {
        HttpRequest requisicao = HttpRequest.newBuilder(endereco.resolve(caminho)).timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .method(metodo, HttpRequest.BodyPublishers.ofString(corpo, StandardCharsets.UTF_8)).build();
        return cliente.http().send(requisicao, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private String idSessao(Navegador cliente) {
        return cliente.cookies().getCookieStore().getCookies().stream()
                .filter(cookie -> cookie.getName().equals("JSESSIONID")).findFirst().orElseThrow().getValue();
    }

    private JsonObject json(HttpResponse<String> resposta) {
        return JsonParser.parseString(resposta.body()).getAsJsonObject();
    }

    private void verificarJson(HttpResponse<String> resposta) {
        assertTrue(resposta.headers().firstValue("Content-Type").orElseThrow().startsWith("application/json"));
        assertEquals("no-store", resposta.headers().firstValue("Cache-Control").orElseThrow());
        assertDoesNotThrow(() -> JsonParser.parseString(resposta.body()));
    }

    private void verificarErro(HttpResponse<String> resposta, int status) {
        assertEquals(status, resposta.statusCode());
        assertTrue(resposta.headers().firstValue("Content-Type").orElseThrow().startsWith("application/json"));
        assertEquals(Set.of("erro"), json(resposta).keySet());
        assertFalse(json(resposta).get("erro").getAsString().isBlank());
    }

    private record Navegador(HttpClient http, CookieManager cookies) {
    }
}
