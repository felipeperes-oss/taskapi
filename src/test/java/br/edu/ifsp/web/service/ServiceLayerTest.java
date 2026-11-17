package br.edu.ifsp.web.service;

import br.edu.ifsp.web.dao.ConnectionFactory;
import br.edu.ifsp.web.model.Tarefa;
import br.edu.ifsp.web.model.Usuario;
import com.google.gson.Gson;
import java.io.StringReader;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.h2.tools.RunScript;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class ServiceLayerTest {
    private final Usuario ana = new Usuario(1, "Ana", "ana@taskapi.test");
    private final Usuario bruno = new Usuario(2, "Bruno", "bruno@taskapi.test");
    private ConnectionFactory connectionFactory;
    private TarefaService tarefas;
    private AutenticacaoService autenticacao;

    @BeforeEach
    void prepararBanco() throws Exception {
        String url = "jdbc:h2:mem:" + UUID.randomUUID()
                + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000";
        connectionFactory = new ConnectionFactory(url, "sa", "");
        String schema = Files.readString(Path.of("schema.sql"))
                .replace("CREATE DATABASE IF NOT EXISTS taskdb;", "")
                .replace("USE taskdb;", "");
        try (Connection conexao = connectionFactory.getConnection()) {
            RunScript.execute(conexao, new StringReader(schema));
        }
        tarefas = new TarefaServiceImpl(connectionFactory);
        autenticacao = new AutenticacaoServiceImpl(connectionFactory);
    }

    @AfterEach
    void encerrarBanco() throws SQLException {
        try (Connection conexao = connectionFactory.getConnection();
             var stmt = conexao.createStatement()) {
            stmt.execute("SHUTDOWN");
        }
    }

    @Test
    void listaSomenteAsTarefasDoUsuario() {
        assertEquals(5, tarefas.listar(ana).size());
        assertEquals(3, tarefas.listar(bruno).size());
        assertTrue(tarefas.listar(ana).stream().allMatch(t -> t.getUsuario().getId() == ana.getId()));
        assertTrue(tarefas.listar(bruno).stream().allMatch(t -> t.getUsuario().getId() == bruno.getId()));
    }

    @Test
    void executaCicloCompletoSemReabrirTarefaConcluida() {
        Tarefa criada = tarefas.criar(ana, "Tarefa nova", null);
        assertTrue(criada.getId() > 0);
        assertFalse(criada.isConcluida());
        assertEquals(ana.getId(), tarefas.buscar(ana, criada.getId()).getUsuario().getId());

        Tarefa atualizada = tarefas.atualizar(ana, criada.getId(), "Titulo alterado", "Descricao");
        assertEquals("Titulo alterado", atualizada.getTitulo());
        assertEquals("Descricao", tarefas.buscar(ana, criada.getId()).getDescricao());

        tarefas.concluir(ana, criada.getId());
        assertTrue(tarefas.buscar(ana, criada.getId()).isConcluida());
        assertThrows(RegraDominioException.class, () -> tarefas.concluir(ana, criada.getId()));
        assertTrue(tarefas.atualizar(ana, criada.getId(), "Outro titulo", null).isConcluida());
        assertTrue(tarefas.buscar(ana, criada.getId()).isConcluida());

        tarefas.excluir(ana, criada.getId());
        assertThrows(RecursoNaoEncontradoException.class, () -> tarefas.buscar(ana, criada.getId()));
        assertThrows(RecursoNaoEncontradoException.class, () -> tarefas.excluir(ana, criada.getId()));
        assertEquals(3, tarefas.listar(bruno).size());
    }

    @ParameterizedTest
    @EnumSource(Acesso.class)
    void tarefaDeOutroUsuarioEquivaleATarefaInexistente(Acesso acesso) {
        Tarefa original = tarefas.listar(bruno).getFirst();
        RecursoNaoEncontradoException alheia = assertThrows(RecursoNaoEncontradoException.class,
                () -> acessar(acesso, ana, original.getId()));
        RecursoNaoEncontradoException ausente = assertThrows(RecursoNaoEncontradoException.class,
                () -> acessar(acesso, ana, Integer.MAX_VALUE));
        assertEquals(ausente.getMessage(), alheia.getMessage());
        Tarefa preservada = tarefas.buscar(bruno, original.getId());
        assertEquals(original.getTitulo(), preservada.getTitulo());
        assertEquals(original.getDescricao(), preservada.getDescricao());
        assertEquals(original.isConcluida(), preservada.isConcluida());
        assertEquals(3, tarefas.listar(bruno).size());
    }

    @Test
    void tarefaAlheiaPermaneceInexistenteMesmoComTituloInvalido() {
        int id = tarefas.listar(bruno).getFirst().getId();
        assertThrows(RecursoNaoEncontradoException.class, () -> tarefas.atualizar(ana, id, null, null));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t\n"})
    void tituloObrigatorioValeNaCriacaoENaAtualizacao(String titulo) {
        Tarefa original = tarefas.listar(ana).getFirst();
        assertThrows(DadosInvalidosException.class, () -> tarefas.criar(ana, titulo, null));
        assertThrows(DadosInvalidosException.class, () -> tarefas.atualizar(ana, original.getId(), titulo, null));
        assertEquals(5, tarefas.listar(ana).size());
        assertEquals(original.getTitulo(), tarefas.buscar(ana, original.getId()).getTitulo());
    }

    @Test
    void respeitaOsLimitesDosCamposSemAlteracaoParcial() {
        Tarefa original = tarefas.listar(ana).getFirst();
        assertThrows(DadosInvalidosException.class, () -> tarefas.criar(ana, "x".repeat(121), null));
        assertThrows(DadosInvalidosException.class, () -> tarefas.criar(ana, "Titulo", "x".repeat(501)));
        assertThrows(DadosInvalidosException.class,
                () -> tarefas.atualizar(ana, original.getId(), "Alterado", "x".repeat(501)));
        assertEquals(original.getTitulo(), tarefas.buscar(ana, original.getId()).getTitulo());
        assertEquals(5, tarefas.listar(ana).size());
        Tarefa limite = tarefas.criar(ana, "x".repeat(120), "x".repeat(500));
        assertEquals(120, tarefas.buscar(ana, limite.getId()).getTitulo().length());
    }

    @Test
    void recusaUsuarioAusenteOuSemIdValidoEmTodasAsOperacoes() {
        for (Usuario usuario : new Usuario[] {null, new Usuario(0, "Ana", "ana@taskapi.test")}) {
            assertThrows(DadosInvalidosException.class, () -> tarefas.listar(usuario));
            assertThrows(DadosInvalidosException.class, () -> tarefas.buscar(usuario, 1));
            assertThrows(DadosInvalidosException.class, () -> tarefas.criar(usuario, "Titulo", null));
            assertThrows(DadosInvalidosException.class, () -> tarefas.atualizar(usuario, 1, "Titulo", null));
            assertThrows(DadosInvalidosException.class, () -> tarefas.concluir(usuario, 1));
            assertThrows(DadosInvalidosException.class, () -> tarefas.excluir(usuario, 1));
        }
    }

    @ParameterizedTest
    @EnumSource(Gravacao.class)
    void falhaDepoisDaGravacaoDesfazTodasAsAlteracoes(Gravacao gravacao) {
        Tarefa original = tarefas.listar(ana).getFirst();
        FactoryMonitorada factory = new FactoryMonitorada(true);
        TarefaService service = new TarefaServiceImpl(factory);
        FalhaInternaException erro = assertThrows(FalhaInternaException.class, () -> {
            switch (gravacao) {
                case CRIAR -> service.criar(ana, "Nao deve persistir", "Descricao");
                case ATUALIZAR -> service.atualizar(ana, original.getId(), "Nao deve persistir", "Descricao");
                case CONCLUIR -> service.concluir(ana, original.getId());
                case EXCLUIR -> service.excluir(ana, original.getId());
            }
        });
        assertInstanceOf(SQLException.class, erro.getCause());
        assertEquals(1, factory.abertas.get());
        assertEquals(1, factory.fechadas.get());
        assertEquals(5, tarefas.listar(ana).size());
        Tarefa preservada = tarefas.buscar(ana, original.getId());
        assertEquals(original.getTitulo(), preservada.getTitulo());
        assertEquals(original.getDescricao(), preservada.getDescricao());
        assertEquals(original.isConcluida(), preservada.isConcluida());
    }

    @Test
    void fechaAConexaoEmSucessoEFalhaDeDominio() {
        FactoryMonitorada factory = new FactoryMonitorada(false);
        TarefaService service = new TarefaServiceImpl(factory);
        Tarefa tarefa = service.criar(ana, "Concluir", null);
        service.concluir(ana, tarefa.getId());
        assertThrows(RegraDominioException.class, () -> service.concluir(ana, tarefa.getId()));
        assertThrows(RecursoNaoEncontradoException.class, () -> service.buscar(ana, Integer.MAX_VALUE));
        assertThrows(DadosInvalidosException.class, () -> service.criar(ana, " ", null));
        assertEquals(5, factory.abertas.get());
        assertEquals(factory.abertas.get(), factory.fechadas.get());
    }

    @Test
    void duasConclusoesConcorrentesResultamEmUmSucessoEUmaViolacao() throws Exception {
        Tarefa tarefa = tarefas.criar(ana, "Conclusao concorrente", null);
        CountDownLatch prontas = new CountDownLatch(2);
        CountDownLatch inicio = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var operacao = (java.util.concurrent.Callable<String>) () -> {
                prontas.countDown();
                if (!inicio.await(5, TimeUnit.SECONDS)) {
                    throw new AssertionError("As operacoes nao iniciaram juntas.");
                }
                try {
                    tarefas.concluir(ana, tarefa.getId());
                    return "concluida";
                } catch (RegraDominioException e) {
                    return "ja concluida";
                }
            };
            var primeira = executor.submit(operacao);
            var segunda = executor.submit(operacao);
            assertTrue(prontas.await(5, TimeUnit.SECONDS));
            inicio.countDown();
            assertEquals(Set.of("concluida", "ja concluida"),
                    Set.of(primeira.get(10, TimeUnit.SECONDS), segunda.get(10, TimeUnit.SECONDS)));
            assertTrue(tarefas.buscar(ana, tarefa.getId()).isConcluida());
        } finally {
            inicio.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void falhaAoAbrirConexaoViraExcecaoDaAplicacao() {
        ConnectionFactory factory = new ConnectionFactory() {
            @Override
            public Connection getConnection() throws SQLException {
                throw new SQLException("Falha simulada na conexao.");
            }
        };
        FalhaInternaException erro = assertThrows(FalhaInternaException.class,
                () -> new TarefaServiceImpl(factory).listar(ana));
        assertInstanceOf(SQLException.class, erro.getCause());
    }

    @Test
    void autenticaAsSenhasDoSchemaSemExporCredenciais() {
        Usuario usuarioAna = autenticacao.autenticar("ana@taskapi.test", "AnaTeste123!");
        Usuario usuarioBruno = autenticacao.autenticar("bruno@taskapi.test", "BrunoTeste123!");
        assertEquals(ana.getId(), usuarioAna.getId());
        assertEquals(bruno.getId(), usuarioBruno.getId());
        assertEquals(Set.of("id", "nome", "email"),
                new Gson().toJsonTree(usuarioAna).getAsJsonObject().keySet());
    }

    @Test
    void senhaErradaEmailAusenteEInjecaoSqlRecebemAMesmaFalha() {
        AutenticacaoException senhaErrada = assertThrows(AutenticacaoException.class,
                () -> autenticacao.autenticar("ana@taskapi.test", "Errada"));
        AutenticacaoException emailAusente = assertThrows(AutenticacaoException.class,
                () -> autenticacao.autenticar("ausente@taskapi.test", "AnaTeste123!"));
        AutenticacaoException injecao = assertThrows(AutenticacaoException.class,
                () -> autenticacao.autenticar("' OR '1'='1", "AnaTeste123!"));
        assertEquals(senhaErrada.getMessage(), emailAusente.getMessage());
        assertEquals(senhaErrada.getMessage(), injecao.getMessage());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t\n"})
    void autenticarRecusaCamposAusentes(String campo) {
        assertThrows(AutenticacaoException.class, () -> autenticacao.autenticar(campo, "AnaTeste123!"));
        assertThrows(AutenticacaoException.class, () -> autenticacao.autenticar("ana@taskapi.test", campo));
    }

    @Test
    void hashArmazenadoInvalidoViraFalhaInterna() throws SQLException {
        try (Connection conexao = connectionFactory.getConnection();
             var stmt = conexao.prepareStatement("UPDATE usuarios SET senha_hash = ? WHERE id = ?")) {
            stmt.setString(1, "hash-invalido");
            stmt.setInt(2, ana.getId());
            stmt.executeUpdate();
        }
        assertThrows(FalhaInternaException.class, () -> autenticacao.autenticar("ana@taskapi.test", "AnaTeste123!"));
    }

    private void acessar(Acesso acesso, Usuario usuario, int id) {
        switch (acesso) {
            case BUSCAR -> tarefas.buscar(usuario, id);
            case ATUALIZAR -> tarefas.atualizar(usuario, id, "Titulo alterado", "Descricao alterada");
            case CONCLUIR -> tarefas.concluir(usuario, id);
            case EXCLUIR -> tarefas.excluir(usuario, id);
        }
    }

    enum Acesso {
        BUSCAR, ATUALIZAR, CONCLUIR, EXCLUIR
    }

    enum Gravacao {
        CRIAR, ATUALIZAR, CONCLUIR, EXCLUIR
    }

    private class FactoryMonitorada extends ConnectionFactory {
        private final boolean falharDepoisDaGravacao;
        private final AtomicInteger abertas = new AtomicInteger();
        private final AtomicInteger fechadas = new AtomicInteger();

        FactoryMonitorada(boolean falharDepoisDaGravacao) {
            this.falharDepoisDaGravacao = falharDepoisDaGravacao;
        }

        @Override
        public Connection getConnection() throws SQLException {
            Connection real = connectionFactory.getConnection();
            abertas.incrementAndGet();
            return (Connection) Proxy.newProxyInstance(getClass().getClassLoader(),
                    new Class<?>[] {Connection.class}, (proxy, metodo, argumentos) -> {
                        try {
                            Object resultado = metodo.invoke(real, argumentos);
                            if (metodo.getName().equals("close")) {
                                fechadas.incrementAndGet();
                            }
                            if (falharDepoisDaGravacao && resultado instanceof PreparedStatement stmt) {
                                return monitorar(stmt);
                            }
                            return resultado;
                        } catch (InvocationTargetException e) {
                            throw e.getCause();
                        }
                    });
        }

        private PreparedStatement monitorar(PreparedStatement real) {
            return (PreparedStatement) Proxy.newProxyInstance(getClass().getClassLoader(),
                    new Class<?>[] {PreparedStatement.class}, (proxy, metodo, argumentos) -> {
                        try {
                            Object resultado = metodo.invoke(real, argumentos);
                            if (metodo.getName().equals("executeUpdate")) {
                                throw new SQLException("Falha simulada depois da gravacao.");
                            }
                            return resultado;
                        } catch (InvocationTargetException e) {
                            throw e.getCause();
                        }
                    });
        }
    }
}
