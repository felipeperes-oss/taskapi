package br.edu.ifsp.web.cli;

import br.edu.ifsp.web.dao.ConnectionFactory;
import br.edu.ifsp.web.model.Usuario;
import br.edu.ifsp.web.service.AutenticacaoService;
import br.edu.ifsp.web.service.AutenticacaoServiceImpl;
import br.edu.ifsp.web.service.FalhaInternaException;
import br.edu.ifsp.web.service.TarefaService;
import br.edu.ifsp.web.service.TarefaServiceImpl;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.PrintStream;
import java.io.StringReader;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.h2.tools.RunScript;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class ImportadorConclusoesTest {
    @TempDir
    Path diretorio;

    private final Usuario ana = new Usuario(1, "Ana", "ana@taskapi.test");
    private final Usuario bruno = new Usuario(2, "Bruno", "bruno@taskapi.test");
    private ConnectionFactory connectionFactory;
    private AutenticacaoService autenticacao;
    private TarefaService tarefas;
    private ImportadorConclusoes importador;

    @BeforeEach
    void preparar() throws Exception {
        String url = "jdbc:h2:mem:" + UUID.randomUUID()
                + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";
        connectionFactory = new ConnectionFactory(url, "sa", "");
        prepararBanco(connectionFactory);
        autenticacao = new AutenticacaoServiceImpl(connectionFactory);
        tarefas = new TarefaServiceImpl(connectionFactory);
        importador = new ImportadorConclusoes(autenticacao, tarefas);
    }

    @AfterEach
    void encerrar() throws SQLException {
        try (Connection conexao = connectionFactory.getConnection();
             var stmt = conexao.createStatement()) {
            stmt.execute("SHUTDOWN");
        }
    }

    @Test
    void processaIdsPropriosAlheiosAusentesERepetidosAteOFim() throws Exception {
        Path arquivo = arquivo("1\n2\n6\n2147483647\n3\n1\n");
        Resultado resultado = executar(importador, arquivo.toString(), "ana@taskapi.test", "AnaTeste123!");
        assertEquals(0, resultado.codigo());
        assertEquals(List.of(
                "Linha 1 (ID 1): concluída",
                "Linha 2 (ID 2): já estava concluída",
                "Linha 3 (ID 6): não encontrada",
                "Linha 4 (ID 2147483647): não encontrada",
                "Linha 5 (ID 3): concluída",
                "Linha 6 (ID 1): já estava concluída"), resultado.saida().lines().toList());
        assertEquals("", resultado.erros());
        assertTrue(tarefas.buscar(ana, 1).isConcluida());
        assertTrue(tarefas.buscar(ana, 3).isConcluida());
        assertFalse(tarefas.buscar(bruno, 6).isConcluida());
        assertEquals(5, tarefas.listar(ana).size());
        assertEquals(3, tarefas.listar(bruno).size());
    }

    @Test
    void concluiSomenteAsTarefasDoUsuarioAutenticado() throws Exception {
        Resultado resultado = executar(importador, arquivo("1\n6\n8\n").toString(),
                "bruno@taskapi.test", "BrunoTeste123!");
        assertEquals(0, resultado.codigo());
        assertTrue(resultado.saida().contains("Linha 1 (ID 1): não encontrada"));
        assertFalse(tarefas.buscar(ana, 1).isConcluida());
        assertTrue(tarefas.buscar(bruno, 6).isConcluida());
        assertTrue(tarefas.buscar(bruno, 8).isConcluida());
    }

    @Test
    void idsInvalidosGeramResultadoEPermitemProcessarAsLinhasSeguintes() throws Exception {
        Resultado resultado = executar(importador, arquivo("abc\n\n0\n-1\n2147483648\n3\n").toString(),
                "ana@taskapi.test", "AnaTeste123!");
        assertEquals(1, resultado.codigo());
        List<String> linhas = resultado.saida().lines().toList();
        assertEquals(6, linhas.size());
        assertEquals(5, linhas.stream().filter(linha -> linha.endsWith("ID inválido.")).count());
        assertEquals("Linha 6 (ID 3): concluída", linhas.getLast());
        assertTrue(tarefas.buscar(ana, 3).isConcluida());
        assertFalse(tarefas.buscar(ana, 1).isConcluida());
        assertEquals("", resultado.erros());
    }

    @Test
    void aceitaArquivoUtf8ComBomEspacosEQuebrasDoWindows() throws Exception {
        Resultado resultado = executar(importador, arquivo("\uFEFF 1 \r\n\t3\t\r\n").toString(),
                "ana@taskapi.test", "AnaTeste123!");
        assertEquals(0, resultado.codigo());
        assertEquals(2, resultado.saida().lines().count());
        assertTrue(tarefas.buscar(ana, 1).isConcluida());
        assertTrue(tarefas.buscar(ana, 3).isConcluida());
    }

    @Test
    void arquivoVazioTerminaSemResultados() throws Exception {
        Resultado resultado = executar(importador, arquivo("").toString(), "ana@taskapi.test", "AnaTeste123!");
        assertEquals(0, resultado.codigo());
        assertEquals("", resultado.saida());
        assertEquals("", resultado.erros());
    }

    @ParameterizedTest
    @CsvSource({"ana@taskapi.test, Errada", "ausente@taskapi.test, AnaTeste123!"})
    void credenciaisInvalidasImpedemOProcessamento(String email, String senha) throws Exception {
        Resultado resultado = executar(importador, arquivo("1\n3\n").toString(), email, senha);
        assertEquals(1, resultado.codigo());
        assertEquals("", resultado.saida());
        assertTrue(resultado.erros().contains("E-mail ou senha inválidos."));
        assertFalse(resultado.erros().contains(senha));
        assertFalse(tarefas.buscar(ana, 1).isConcluida());
        assertFalse(tarefas.buscar(ana, 3).isConcluida());
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2, 4})
    void exigeExatamenteTresArgumentosSemAutenticar(int quantidade) {
        AutenticacaoService semUso = (email, senha) -> {
            fail("Nao deve autenticar quando a quantidade de argumentos e invalida.");
            return null;
        };
        String[] args = new String[quantidade];
        java.util.Arrays.fill(args, "valor");
        Resultado resultado = executar(new ImportadorConclusoes(semUso, tarefas), args);
        assertEquals(1, resultado.codigo());
        assertEquals("", resultado.saida());
        assertTrue(resultado.erros().startsWith("Uso: ImportadorConclusoes"));
    }

    @Test
    void arquivoAusenteRetornaFalhaSemAlterarTarefas() {
        Resultado resultado = executar(importador, diretorio.resolve("ausente.txt").toString(),
                "ana@taskapi.test", "AnaTeste123!");
        assertEquals(1, resultado.codigo());
        assertTrue(resultado.erros().contains("arquivo"));
        assertEquals("", resultado.saida());
        assertFalse(tarefas.buscar(ana, 1).isConcluida());
    }

    @Test
    void caminhoInvalidoRetornaFalhaSemStackTrace() {
        Resultado resultado = executar(importador, "\u0000", "ana@taskapi.test", "AnaTeste123!");
        assertEquals(1, resultado.codigo());
        assertTrue(resultado.erros().contains("arquivo"));
        assertFalse(resultado.erros().contains("Exception"));
    }

    @Test
    void falhaEmUmaConclusaoNaoImpedeAProxima() throws Exception {
        TarefaService service = (TarefaService) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[] {TarefaService.class}, (proxy, metodo, args) -> {
                    if (metodo.getName().equals("concluir") && (Integer) args[1] == 1) {
                        throw new FalhaInternaException("Detalhes privados.", new SQLException("Falha simulada."));
                    }
                    try {
                        return metodo.invoke(tarefas, args);
                    } catch (InvocationTargetException e) {
                        throw e.getCause();
                    }
                });
        Resultado resultado = executar(new ImportadorConclusoes(autenticacao, service),
                arquivo("1\n3\n").toString(), "ana@taskapi.test", "AnaTeste123!");
        assertEquals(1, resultado.codigo());
        assertEquals(List.of("Linha 1 (ID 1): falha ao concluir", "Linha 2 (ID 3): concluída"),
                resultado.saida().lines().toList());
        assertFalse(tarefas.buscar(ana, 1).isConcluida());
        assertTrue(tarefas.buscar(ana, 3).isConcluida());
        assertFalse(resultado.saida().contains("privados"));
    }

    @Test
    void falhaNaAutenticacaoTerminaComErroSemProcessarIds() throws Exception {
        AutenticacaoService service = (email, senha) -> {
            throw new FalhaInternaException("Detalhes privados.", new SQLException("Falha simulada."));
        };
        Resultado resultado = executar(new ImportadorConclusoes(service, tarefas),
                arquivo("1\n").toString(), "ana@taskapi.test", "AnaTeste123!");
        assertEquals(1, resultado.codigo());
        assertEquals("", resultado.saida());
        assertTrue(resultado.erros().contains("autenticar"));
        assertFalse(resultado.erros().contains("privados"));
        assertFalse(resultado.erros().contains("AnaTeste123!"));
        assertFalse(tarefas.buscar(ana, 1).isConcluida());
    }

    @Test
    void mainExecutaEmOutroProcessoSemTomcatOuBibliotecaServlet() throws Exception {
        ConnectionFactory factory = bancoEmArquivo();
        Path ids = arquivo("1\n6\n2147483647\n2\n3\n1\n");
        Resultado resultado = executarProcesso(factoryUrl(), ids.toString(), "ana@taskapi.test", "AnaTeste123!");
        assertEquals(0, resultado.codigo(), resultado.erros());
        assertEquals(6, resultado.saida().lines().count());
        assertTrue(resultado.saida().contains("Linha 1 (ID 1): concluída"));
        assertTrue(resultado.saida().contains("Linha 2 (ID 6): não encontrada"));
        assertTrue(resultado.saida().contains("Linha 6 (ID 1): já estava concluída"));
        assertEquals("", resultado.erros());
        TarefaService service = new TarefaServiceImpl(factory);
        assertTrue(service.buscar(ana, 1).isConcluida());
        assertTrue(service.buscar(ana, 3).isConcluida());
        assertFalse(service.buscar(bruno, 6).isConcluida());
    }

    @Test
    void mainRetornaCodigoNaoZeroComSenhaErrada() throws Exception {
        ConnectionFactory factory = bancoEmArquivo();
        Resultado resultado = executarProcesso(factoryUrl(), arquivo("1\n").toString(), "ana@taskapi.test", "Errada");
        assertNotEquals(0, resultado.codigo());
        assertEquals("", resultado.saida());
        assertTrue(resultado.erros().contains("E-mail ou senha inválidos."));
        assertFalse(new TarefaServiceImpl(factory).buscar(ana, 1).isConcluida());
    }

    @Test
    void mainRetornaCodigoNaoZeroComArgumentosAusentes() throws Exception {
        Resultado resultado = executarProcesso(factoryUrl());
        assertNotEquals(0, resultado.codigo());
        assertEquals("", resultado.saida());
        assertTrue(resultado.erros().startsWith("Uso: ImportadorConclusoes"));
    }

    private void prepararBanco(ConnectionFactory factory) throws Exception {
        String schema = Files.readString(Path.of("schema.sql"))
                .replace("CREATE DATABASE IF NOT EXISTS taskdb;", "")
                .replace("USE taskdb;", "");
        try (Connection conexao = factory.getConnection()) {
            RunScript.execute(conexao, new StringReader(schema));
        }
    }

    private Path arquivo(String conteudo) throws Exception {
        return Files.writeString(diretorio.resolve("ids.txt"), conteudo, StandardCharsets.UTF_8);
    }

    private ConnectionFactory bancoEmArquivo() throws Exception {
        ConnectionFactory factory = new ConnectionFactory(factoryUrl(), "sa", "");
        prepararBanco(factory);
        return factory;
    }

    private String factoryUrl() {
        return "jdbc:h2:file:" + diretorio.resolve("banco").toAbsolutePath().toString().replace('\\', '/')
                + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE";
    }

    private Resultado executar(ImportadorConclusoes cli, String... args) {
        ByteArrayOutputStream saida = new ByteArrayOutputStream();
        ByteArrayOutputStream erros = new ByteArrayOutputStream();
        try (PrintStream out = new PrintStream(saida, true, StandardCharsets.UTF_8);
             PrintStream err = new PrintStream(erros, true, StandardCharsets.UTF_8)) {
            int codigo = cli.executar(args, out, err);
            return new Resultado(codigo, saida.toString(StandardCharsets.UTF_8), erros.toString(StandardCharsets.UTF_8));
        }
    }

    private Resultado executarProcesso(String url, String... args) throws Exception {
        String java = Path.of(System.getProperty("java.home"), "bin",
                System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java").toString();
        String classpath = String.join(File.pathSeparator, Path.of("target/classes").toAbsolutePath().toString(),
                Path.of(com.mysql.cj.jdbc.Driver.class.getProtectionDomain().getCodeSource().getLocation().toURI()).toString(),
                Path.of(org.h2.Driver.class.getProtectionDomain().getCodeSource().getLocation().toURI()).toString());
        List<String> comando = new ArrayList<>(List.of(java, "-Dfile.encoding=UTF-8", "-Dstdout.encoding=UTF-8",
                "-Dstderr.encoding=UTF-8", "-cp", classpath, ImportadorConclusoes.class.getName()));
        comando.addAll(List.of(args));
        Path saida = diretorio.resolve("saida.txt");
        Path erros = diretorio.resolve("erros.txt");
        ProcessBuilder builder = new ProcessBuilder(comando).redirectOutput(saida.toFile()).redirectError(erros.toFile());
        builder.environment().put("TASKAPI_DB_URL", url);
        builder.environment().put("TASKAPI_DB_USER", "sa");
        builder.environment().put("TASKAPI_DB_PASSWORD", "");
        Process processo = builder.start();
        try {
            assertTrue(processo.waitFor(15, TimeUnit.SECONDS), "O importador nao encerrou no prazo.");
            return new Resultado(processo.exitValue(), Files.readString(saida), Files.readString(erros));
        } finally {
            if (processo.isAlive()) {
                processo.destroyForcibly();
                processo.waitFor(5, TimeUnit.SECONDS);
            }
        }
    }

    private record Resultado(int codigo, String saida, String erros) {
    }
}
