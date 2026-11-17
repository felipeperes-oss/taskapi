package br.edu.ifsp.web.cli;

import br.edu.ifsp.web.dao.ConnectionFactory;
import br.edu.ifsp.web.model.Usuario;
import br.edu.ifsp.web.service.AplicacaoException;
import br.edu.ifsp.web.service.AutenticacaoException;
import br.edu.ifsp.web.service.AutenticacaoService;
import br.edu.ifsp.web.service.AutenticacaoServiceImpl;
import br.edu.ifsp.web.service.DadosInvalidosException;
import br.edu.ifsp.web.service.RecursoNaoEncontradoException;
import br.edu.ifsp.web.service.RegraDominioException;
import br.edu.ifsp.web.service.TarefaService;
import br.edu.ifsp.web.service.TarefaServiceImpl;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;

public class ImportadorConclusoes {
    private final AutenticacaoService autenticacao;
    private final TarefaService tarefas;

    public ImportadorConclusoes(AutenticacaoService autenticacao, TarefaService tarefas) {
        this.autenticacao = autenticacao;
        this.tarefas = tarefas;
    }

    public static void main(String[] args) {
        ConnectionFactory connectionFactory = new ConnectionFactory();
        AutenticacaoService autenticacao = new AutenticacaoServiceImpl(connectionFactory);
        TarefaService tarefas = new TarefaServiceImpl(connectionFactory);
        int codigo = new ImportadorConclusoes(autenticacao, tarefas).executar(args, System.out, System.err);
        if (codigo != 0) {
            System.exit(codigo);
        }
    }

    public int executar(String[] args, PrintStream saida, PrintStream erros) {
        if (args.length != 3) {
            erros.println("Uso: ImportadorConclusoes <arquivo> <email> <senha>");
            return 1;
        }
        Usuario usuario;
        try {
            usuario = autenticacao.autenticar(args[1], args[2]);
        } catch (AutenticacaoException e) {
            erros.println(e.getMessage());
            return 1;
        } catch (AplicacaoException e) {
            erros.println("Não foi possível autenticar o usuário.");
            return 1;
        }
        try (BufferedReader leitor = Files.newBufferedReader(Path.of(args[0]), StandardCharsets.UTF_8)) {
            int numero = 0;
            boolean houveFalha = false;
            String linha;
            while ((linha = leitor.readLine()) != null) {
                numero++;
                if (numero == 1 && linha.startsWith("\uFEFF")) {
                    linha = linha.substring(1);
                }
                if (!processar(linha, numero, usuario, saida)) {
                    houveFalha = true;
                }
            }
            return houveFalha ? 1 : 0;
        } catch (IOException | InvalidPathException e) {
            erros.println("Não foi possível ler o arquivo informado.");
            return 1;
        }
    }

    private boolean processar(String linha, int numero, Usuario usuario, PrintStream saida) {
        int id;
        try {
            id = Integer.parseInt(linha.strip());
        } catch (NumberFormatException e) {
            saida.println("Linha " + numero + ": ID inválido.");
            return false;
        }
        if (id <= 0) {
            saida.println("Linha " + numero + ": ID inválido.");
            return false;
        }
        String resultado;
        boolean sucesso = true;
        try {
            tarefas.concluir(usuario, id);
            resultado = "concluída";
        } catch (RecursoNaoEncontradoException e) {
            resultado = "não encontrada";
        } catch (RegraDominioException e) {
            resultado = "já estava concluída";
        } catch (DadosInvalidosException e) {
            resultado = "dados inválidos";
            sucesso = false;
        } catch (AplicacaoException e) {
            resultado = "falha ao concluir";
            sucesso = false;
        }
        saida.println("Linha " + numero + " (ID " + id + "): " + resultado);
        return sucesso;
    }
}
