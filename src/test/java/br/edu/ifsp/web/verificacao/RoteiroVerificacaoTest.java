package br.edu.ifsp.web.verificacao;

import br.edu.ifsp.web.dao.ConnectionFactory;
import br.edu.ifsp.web.model.Usuario;
import br.edu.ifsp.web.service.AutenticacaoService;
import br.edu.ifsp.web.service.AutenticacaoServiceImpl;
import br.edu.ifsp.web.service.TarefaService;
import br.edu.ifsp.web.service.TarefaServiceImpl;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.File;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import org.apache.catalina.Context;
import org.apache.catalina.startup.ContextConfig;
import org.apache.catalina.startup.Tomcat;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named = "TASKAPI_VERIFICAR_09", matches = "true")
class RoteiroVerificacaoTest {
    private final Path pasta = Path.of("target/verificacao-09").toAbsolutePath();
    private final Path cookies = pasta.resolve("cookies.txt");
    private final List<String> relatorio = new ArrayList<>();
    private final ConnectionFactory connectionFactory = new ConnectionFactory();
    private String endereco;
    private int requisicao;

    @Test
    void executaAsOnzeEtapasComCurlEImportadorNoMesmoMySql() throws Exception {
        Files.createDirectories(pasta);
        Path war = Path.of("target/atv_aval.war").toAbsolutePath();
        assertTrue(Files.isRegularFile(war), "Gere o WAR antes de executar o roteiro.");
        String banco;
        try (Connection conexao = connectionFactory.getConnection()) {
            assertEquals("MySQL", conexao.getMetaData().getDatabaseProductName());
            banco = conexao.getMetaData().getDatabaseProductName() + " " + conexao.getMetaData().getDatabaseProductVersion();
        }
        AutenticacaoService autenticacao = new AutenticacaoServiceImpl(connectionFactory);
        Usuario ana = autenticacao.autenticar("ana@taskapi.test", "AnaTeste123!");
        TarefaService tarefas = new TarefaServiceImpl(connectionFactory);
        tarefas.criar(ana, "Pendência 1 do roteiro de verificação", null);
        tarefas.criar(ana, "Pendência 2 do roteiro de verificação", null);
        relatorio.add("# Verificação do tópico 9");
        relatorio.add("");
        relatorio.add("Ambiente: " + banco + ", Tomcat integrado, curl e importador em outro processo Java.");
        relatorio.add("");
        relatorio.add("O roteiro usa um banco de teste isolado e mantém os cookies entre as requisições.");
        relatorio.add("");
        relatorio.add("| Etapa | Resultado observado | Estado |");
        relatorio.add("| --- | --- | --- |");
        Files.writeString(pasta.resolve("resultados.md"), String.join("\n", relatorio) + "\n");

        Tomcat tomcat = new Tomcat();
        Files.createDirectories(pasta.resolve("tomcat/webapps"));
        tomcat.setBaseDir(pasta.resolve("tomcat").toString());
        tomcat.setPort(0);
        tomcat.setAddDefaultWebXmlToWebapp(false);
        tomcat.getConnector().setProperty("address", "127.0.0.1");
        ContextConfig configuracao = new ContextConfig();
        configuracao.setDefaultWebXml(tomcat.noDefaultWebXmlPath());
        Context contexto = tomcat.addWebapp(tomcat.getHost(), "/atv_aval", war.toString(), configuracao);
        contexto.setParentClassLoader(getClass().getClassLoader());
        PrintStream original = System.out;
        try (PrintStream console = new PrintStream(Files.newOutputStream(pasta.resolve("console.log")),
                true, StandardCharsets.UTF_8)) {
            System.setOut(console);
            try {
                tomcat.start();
                assertTrue(contexto.getState().isAvailable());
                endereco = "http://127.0.0.1:" + tomcat.getConnector().getLocalPort() + "/atv_aval";
                verificar(contexto);
            } finally {
                try {
                    tomcat.stop();
                    tomcat.destroy();
                } finally {
                    System.setOut(original);
                }
            }
        }
        String console = Files.readString(pasta.resolve("console.log"));
        assertTrue(console.contains("Sessão criada:"));
        assertTrue(console.contains("Sessão destruída:"));
        assertTrue(console.contains("levou (ms):"));
        assertTrue(console.contains("sessões ativas: 0"));
    }

    private void verificar(Context contexto) throws Exception {
        String idAnterior = contexto.getManager().createSession(null).getId();
        Files.writeString(cookies, "127.0.0.1\tFALSE\t/atv_aval\tFALSE\t0\tJSESSIONID\t" + idAnterior + "\n");

        erro(curl("GET", "/tarefas", null), 401);
        registrar(1, "GET /tarefas sem login: 401 com JSON de erro.");

        erro(curl("POST", "/auth/login", "{\"email\":\"ana@taskapi.test\",\"senha\":\"Errada\"}"), 401);
        assertEquals(idAnterior, idSessao());
        registrar(2, "Login com senha errada: 401.");

        Resposta login = curl("POST", "/auth/login", "{\"email\":\"ana@taskapi.test\",\"senha\":\"AnaTeste123!\"}");
        assertEquals(200, login.status());
        JsonObject usuario = login.json();
        assertEquals(Set.of("id", "nome", "email"), usuario.keySet());
        assertEquals("ana@taskapi.test", usuario.get("email").getAsString());
        assertNotEquals(idAnterior, idSessao());
        assertNull(contexto.getManager().findSession(idAnterior));
        int usuarioId = usuario.get("id").getAsInt();
        registrar(3, "Login de Ana: 200, sem credencial e com identificador de sessão alterado.");

        Resposta lista = curl("GET", "/tarefas", null);
        assertEquals(200, lista.status());
        JsonArray tarefas = JsonParser.parseString(lista.corpo()).getAsJsonArray();
        assertTrue(tarefas.size() >= 3);
        List<Integer> pendentes = new ArrayList<>();
        for (var item : tarefas) {
            JsonObject tarefa = item.getAsJsonObject();
            assertEquals(usuarioId, tarefa.getAsJsonObject("usuario").get("id").getAsInt());
            if (!tarefa.get("concluida").getAsBoolean()) {
                pendentes.add(tarefa.get("id").getAsInt());
            }
        }
        assertTrue(pendentes.size() >= 2, "Use dados de teste com duas tarefas pendentes de Ana.");
        registrar(4, "GET /tarefas: 200, " + tarefas.size() + " tarefas, todas de Ana.");

        EstadoTarefa original = tarefaDeBruno();
        for (String metodo : List.of("GET", "PUT", "PATCH", "DELETE")) {
            String corpo = metodo.equals("PUT") ? "{\"titulo\":\"Tentativa indevida\"}" : null;
            erro(curl(metodo, "/tarefas/" + original.id(), corpo), 404);
        }
        assertEquals(original, estado(original.id()));
        registrar(5, "GET, PUT, PATCH e DELETE na tarefa de Bruno: 404 em todas; dados preservados.");

        erro(curl("POST", "/tarefas", "{\"descricao\":\"Sem título\"}"), 400);
        registrar(6, "POST sem título: 400.");

        Resposta criacao = curl("POST", "/tarefas", "{\"titulo\":\"Verificação do tópico 9\",\"descricao\":\"Criada por curl\"}");
        assertEquals(201, criacao.status());
        int criadaId = criacao.json().get("id").getAsInt();
        assertTrue(criadaId > 0);
        assertEquals(usuarioId, estado(criadaId).usuarioId());
        assertFalse(estado(criadaId).concluida());
        registrar(7, "POST válido: 201 com ID gerado " + criadaId + ".");

        Resposta primeiraConclusao = curl("PATCH", "/tarefas/" + criadaId, null);
        assertEquals(204, primeiraConclusao.status());
        assertEquals("", primeiraConclusao.corpo());
        erro(curl("PATCH", "/tarefas/" + criadaId, null), 409);
        assertTrue(estado(criadaId).concluida());
        registrar(8, "PATCH repetido: 204 sem corpo, seguido de 409; tarefa concluída.");

        Resposta me = curl("GET", "/auth/me", null);
        assertEquals(200, me.status());
        assertEquals(usuario, me.json().getAsJsonObject("usuario"));
        AtomicInteger sessoes = (AtomicInteger) contexto.getServletContext().getAttribute("sessoesAtivas");
        assertEquals(sessoes.get(), me.json().get("sessoesAtivas").getAsInt());
        assertEquals(1, sessoes.get());
        registrar(9, "GET /auth/me: 200, usuário correto e uma sessão ativa.");

        Resposta logout = curl("POST", "/auth/logout", null);
        assertEquals(204, logout.status());
        assertEquals("", logout.corpo());
        erro(curl("GET", "/tarefas", null), 401);
        assertEquals(0, sessoes.get());
        registrar(10, "Logout: 204; GET /tarefas em seguida: 401; zero sessões ativas.");

        verificarImportador(pendentes.get(0), pendentes.get(1), original, criadaId);
        registrar(11, "Importador: concluiu as duas tarefas de Ana, informou a de Bruno e a inexistente, reconheceu as concluídas e chegou ao fim do arquivo; saída 0.");
    }

    private void verificarImportador(int primeira, int segunda, EstadoTarefa bruno, int concluida) throws Exception {
        Path ids = pasta.resolve("11-ids.txt");
        Files.writeString(ids, primeira + "\n" + bruno.id() + "\n2147483647\n" + concluida + "\n" + segunda + "\n" + primeira + "\n");
        Path aplicacao = Path.of("target/atv_aval/WEB-INF").toAbsolutePath();
        List<String> classpath = new ArrayList<>(List.of(aplicacao.resolve("classes").toString()));
        try (Stream<Path> bibliotecas = Files.list(aplicacao.resolve("lib"))) {
            bibliotecas.filter(path -> path.toString().endsWith(".jar")).sorted()
                    .forEach(path -> classpath.add(path.toString()));
        }
        assertFalse(classpath.stream().anyMatch(path -> path.contains("tomcat-") || path.contains("servlet-api")));
        String java = Path.of(System.getProperty("java.home"), "bin",
                System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java").toString();
        Path saida = pasta.resolve("11-importador.stdout.txt");
        Path erros = pasta.resolve("11-importador.stderr.txt");
        Process processo = new ProcessBuilder(java, "-Dfile.encoding=UTF-8", "-Dstdout.encoding=UTF-8",
                "-Dstderr.encoding=UTF-8", "-cp", String.join(File.pathSeparator, classpath),
                "br.edu.ifsp.web.cli.ImportadorConclusoes", ids.toString(), "ana@taskapi.test", "AnaTeste123!")
                .redirectOutput(saida.toFile()).redirectError(erros.toFile()).start();
        esperar(processo);
        assertEquals(0, processo.exitValue(), Files.readString(erros));
        assertEquals("", Files.readString(erros));
        assertEquals(List.of("Linha 1 (ID " + primeira + "): concluída",
                "Linha 2 (ID " + bruno.id() + "): não encontrada",
                "Linha 3 (ID 2147483647): não encontrada",
                "Linha 4 (ID " + concluida + "): já estava concluída",
                "Linha 5 (ID " + segunda + "): concluída",
                "Linha 6 (ID " + primeira + "): já estava concluída"), Files.readAllLines(saida));
        assertTrue(estado(primeira).concluida());
        assertTrue(estado(segunda).concluida());
        assertEquals(bruno, estado(bruno.id()));
    }

    private Resposta curl(String metodo, String caminho, String corpo) throws Exception {
        String prefixo = String.format("%02d", ++requisicao);
        Path resposta = pasta.resolve(prefixo + "-resposta.json");
        Path status = pasta.resolve(prefixo + "-status.txt");
        Path erros = pasta.resolve(prefixo + "-curl.stderr.txt");
        String curl = System.getProperty("os.name").startsWith("Windows") ? "curl.exe" : "curl";
        List<String> comando = new ArrayList<>(List.of(curl, "--silent", "--show-error", "--max-time", "10",
                "--request", metodo, "--cookie", cookies.toString(), "--cookie-jar", cookies.toString(),
                "--output", resposta.toString(), "--dump-header", pasta.resolve(prefixo + "-headers.txt").toString(),
                "--write-out", "%{http_code}"));
        if (corpo != null) {
            Path entrada = pasta.resolve(prefixo + "-entrada.json");
            Files.writeString(entrada, corpo);
            comando.addAll(List.of("--header", "Content-Type: application/json", "--data-binary", "@" + entrada));
        }
        comando.add(endereco + caminho);
        Process processo = new ProcessBuilder(comando).redirectOutput(status.toFile()).redirectError(erros.toFile()).start();
        esperar(processo);
        assertEquals(0, processo.exitValue(), Files.readString(erros));
        return new Resposta(Integer.parseInt(Files.readString(status).strip()), Files.readString(resposta));
    }

    private void esperar(Process processo) throws Exception {
        try {
            assertTrue(processo.waitFor(20, TimeUnit.SECONDS), "O processo não encerrou no prazo.");
        } finally {
            if (processo.isAlive()) {
                processo.destroyForcibly();
                processo.waitFor(5, TimeUnit.SECONDS);
            }
        }
    }

    private String idSessao() throws Exception {
        return Files.readAllLines(cookies).stream().filter(linha -> linha.contains("\tJSESSIONID\t"))
                .map(linha -> linha.substring(linha.lastIndexOf('\t') + 1)).findFirst().orElseThrow();
    }

    private EstadoTarefa tarefaDeBruno() throws Exception {
        String sql = "SELECT t.id FROM tarefas t JOIN usuarios u ON u.id = t.usuario_id WHERE u.email = ? ORDER BY t.id LIMIT 1";
        try (Connection conexao = connectionFactory.getConnection();
             var stmt = conexao.prepareStatement(sql)) {
            stmt.setString(1, "bruno@taskapi.test");
            try (var rs = stmt.executeQuery()) {
                assertTrue(rs.next());
                return estado(rs.getInt("id"));
            }
        }
    }

    private EstadoTarefa estado(int id) throws Exception {
        try (Connection conexao = connectionFactory.getConnection();
             var stmt = conexao.prepareStatement("SELECT id, titulo, descricao, concluida, usuario_id FROM tarefas WHERE id = ?")) {
            stmt.setInt(1, id);
            try (var rs = stmt.executeQuery()) {
                assertTrue(rs.next());
                return new EstadoTarefa(rs.getInt("id"), rs.getString("titulo"), rs.getString("descricao"),
                        rs.getBoolean("concluida"), rs.getInt("usuario_id"));
            }
        }
    }

    private void erro(Resposta resposta, int status) {
        assertEquals(status, resposta.status());
        assertEquals(Set.of("erro"), resposta.json().keySet());
        assertFalse(resposta.json().get("erro").getAsString().isBlank());
    }

    private void registrar(int etapa, String resultado) throws Exception {
        relatorio.add("| " + etapa + " | " + resultado + " | PASSOU |");
        Files.writeString(pasta.resolve("resultados.md"), String.join("\n", relatorio) + "\n");
    }

    private record EstadoTarefa(int id, String titulo, String descricao, boolean concluida, int usuarioId) {
    }

    private record Resposta(int status, String corpo) {
        JsonObject json() {
            return JsonParser.parseString(corpo).getAsJsonObject();
        }
    }
}
