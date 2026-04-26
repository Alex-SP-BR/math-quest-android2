package com.alex.mathquest

import android.media.SoundPool
import android.content.Context
import android.media.MediaPlayer
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import kotlin.random.Random

import net.objecthunter.exp4j.ExpressionBuilder

class MainActivity : AppCompatActivity() {

    // --- UI ---
    private lateinit var txtPergunta: TextView
    private lateinit var edtResposta: EditText
    private lateinit var btnResponder: Button
    private lateinit var txtScore: TextView
    private lateinit var txtErros: TextView
    private lateinit var txtRecorde: TextView
    private lateinit var progressoNivel: ProgressBar
    private lateinit var txtNivel: TextView // Texto acima da barra

    // --- Variáveis do jogo ---
    private var score = 0
    private var erros = 0
    private var plays = 0
    private var respostaCorreta = 0.0
    private var recorde = 0
    private var playerName: String = "Jogador (Player)"
    private var nivel = 1
    private var progresso = 0 // 0 a 100

    private val rand = Random(System.nanoTime())

    private val prefsName = "MathQuestPrefs"
    private val keyRecordes = "ranking"

    // --- Áudio ---
    private var ambientPlayer: MediaPlayer? = null
    private var ambientIndex = 0
    private val ambientList = listOf(
        R.raw.gaivota,
        R.raw.flauta,
        R.raw.mysticforestambient,
        R.raw.insetos,
        R.raw.musicaangelical,
        R.raw.aguaescorrendo,
        R.raw.passarinho,
        R.raw.aguamisterio,
        R.raw.piano,
        R.raw.overthemediantandfarbeyondcinematiccue
    )

    // Para efeitos curtoss
    private lateinit var soundPool: SoundPool
    private var sfxErrorId: Int = 0
    private var sfxLevelUpId: Int = 0
    private var sfxEndId: Int = 0


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // --- Conecta UI ---
        txtPergunta = findViewById(R.id.txtPergunta)
        edtResposta = findViewById(R.id.edtResposta)
        btnResponder = findViewById(R.id.btnResponder)
        // Permite responder apertando "OK" ou "Enter" no teclado
        edtResposta.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_DONE ||
                actionId == android.view.inputmethod.EditorInfo.IME_ACTION_GO ||
                actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEND ||
                actionId == android.view.inputmethod.EditorInfo.IME_ACTION_NEXT ||
                actionId == android.view.inputmethod.EditorInfo.IME_NULL) {

                btnResponder.performClick() // Simula clique no botão "Responder"
                true
            } else {
                false
            }
        }

        txtScore = findViewById(R.id.txtScore)
        txtErros = findViewById(R.id.txtErros)
        txtRecorde = findViewById(R.id.txtRecorde)
        progressoNivel = findViewById(R.id.progressoNivel)
        txtNivel = findViewById(R.id.txtNivel)
        val btnSair: Button = findViewById(R.id.btnSair)
        val btnCompartilhar: Button = findViewById(R.id.btnCompartilhar)


        btnSair.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Sair do jogo (Exit game)")
                .setMessage("Tem certeza que quer sair (Are you sure you want to exit)?")
                .setPositiveButton("Sim(Yes)") { dialog, _ ->
                    salvarRanking() // salva score e nível se estiver no top 5
                    dialog.dismiss()
                    finish() // fecha o jogo
                }
                .setNegativeButton("Não(No)") { dialog, _ ->
                    dialog.dismiss() // volta ao jogo
                }
                .show()
        }


        btnCompartilhar.setOnClickListener {
            val mensagem = """
        Meus resultados no Math Quest (My results in Math Quest):
        
        Nível (Level): $nivel
        Pontos (Points): $score
        Acertos (Hits): ${plays - erros}

        Desafie-se também (Try it yourself)!
        
        https://play.google.com/store/apps/details?id=com.alex.mathquest
    """.trimIndent()

            val intent = android.content.Intent().apply {
                action = android.content.Intent.ACTION_SEND
                putExtra(android.content.Intent.EXTRA_TEXT, mensagem)
                type = "text/plain"
            }

            val chooser = android.content.Intent.createChooser(intent, "Compartilhar via (Share via)")
            startActivity(chooser)
        }





        carregarRecordeEAtualizarUI()
        atualizarNivel()
        pedirNomeJogador()

        // Inicializa efeitos curtos com SoundPool
        soundPool = SoundPool.Builder().setMaxStreams(5).build()
        sfxErrorId = soundPool.load(this, R.raw.error, 1)
        sfxLevelUpId = soundPool.load(this, R.raw.lvlup, 1)
        sfxEndId = soundPool.load(this, R.raw.zerarjogo, 1)

// Inicializa a primeira música ambiente
        ambientIndex = 0
        ambientPlayer = MediaPlayer.create(this, ambientList[ambientIndex])
        ambientPlayer?.isLooping = false
        ambientPlayer?.setOnCompletionListener { playNextAmbient() }
        ambientPlayer?.start()


        btnResponder.setOnClickListener {
            val respostaTexto = edtResposta.text.toString().replace(',', '.').trim()
            val respostaUsuario = respostaTexto.toDoubleOrNull()

            if (respostaUsuario == null) {
                Toast.makeText(this, "Digite um número válido (Enter a valid number)!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val EPS = 0.0001
            if (kotlin.math.abs(respostaUsuario - respostaCorreta) < EPS) {
                score++

                // Calcula quantos acertos são necessários para subir de nível
                val acertosNecessarios = when (nivel) {
                    1 -> 3 // lvl 1 → 2
                    2 -> 4 // lvl 2 → 3
                    3, 4 -> 4 // lvl 3–4 → 5
                    in 5..10 -> 5 // lvl 5–10 → 6
                    in 11..30 -> 6 // lvl 11–30 → 7
                    in 31..50 -> 7 // lvl 31–50 → 8
                    in 51..70 -> 8 // lvl 51–70 → 9
                    in 71..90 -> 9 // lvl 71–90 → 10
                    else -> 10 // lvl 91–100 → fim
                }

                // Cada acerto vale uma fração do progresso total
                val progressoPorAcerto = 100 / acertosNecessarios
                progresso += progressoPorAcerto

                if (progresso >= 100) {
                    progresso = 0
                    nivel++

                    // 🔊 Toca som de subir de nível
                    playLevelUp()

                    if (nivel > 100) {
                        // 🔊 Música de fim de jogo
                        playEndSong()

                        salvarRanking()
                        AlertDialog.Builder(this)
                            .setTitle("🏆 Parabéns (Congratulations)!")
                            .setMessage("Você chegou ao nível 100 (You reached level 100)!\nFim de jogo (Game Over)!\nPontuação final (Score): $score")
                            .setCancelable(false)
                            .setPositiveButton("Reiniciar (Restart)") { dialog, _ ->
                                score = 0
                                erros = 0
                                plays = 0
                                progresso = 0
                                nivel = 1
                                progressoNivel.progress = 0
                                atualizarNivel()
                                atualizarPlacar()
                                gerarNovaPergunta()
                                dialog.dismiss()
                            }
                            .setNegativeButton("Sair (Exit)") { dialog, _ ->
                                dialog.dismiss()
                                finish()
                            }
                            .show()
                        return@setOnClickListener
                    } else {
                        Toast.makeText(this, "🧠 Level $nivel! Raciocínio afiado (Sharp thinking)!", Toast.LENGTH_SHORT).show()
                    }
                }
                else {
                    mostrarFraseMotivacional()
                }
            }

            else {
                erros++
                progresso = (progresso - 10).coerceAtLeast(0)
                val msgCorreta = if (respostaCorreta % 1.0 == 0.0)
                    respostaCorreta.toInt().toString()
                else String.format("%.2f", respostaCorreta)
                Toast.makeText(this, "❌ Errado! Resposta correta (Wrong! Correct answer): $msgCorreta", Toast.LENGTH_SHORT).show()

                // 🔊 Toca som de erro
                playError()
            }


            progressoNivel.progress = progresso
            atualizarNivel()
            plays++
            atualizarPlacar()

            if (erros >= 3) {
                mostrarRankingDialog()
            } else {
                gerarNovaPergunta()
            }
        }
    }

    private fun atualizarNivel() {
        txtNivel.text = "Lvl $nivel"
    }

    private fun mostrarFraseMotivacional() {
        val frases = listOf(
            "Seu cérebro está pegando fogo (Brain on fire)! 🔥",
            "Você está ficando mais rápido (Getting faster)! ⚡",
            "Excelente foco, continue assim (Excellent focus! Keep it up)! 💪",
            "Matemática está no sangue (Math in your blood)! 🧬",
            "Sua mente está evoluindo (Mind evolving)! 🚀",
            "Pensamento afiado (Sharp mind)! ✨",
            "Você está calculando como um mestre (Math master)! 🧠",
            "Está dominando os números (Numbers under control)! 🎯",
            "Cálculo perfeito (Perfect calculation)! ✔️",
            "Você está no ritmo certo (Right pace)! 📈",
            "Seu raciocínio está evoluindo (Reasoning evolving)! 🔄",
            "Isso é disciplina pura (Pure discipline)! 💪",
            "Você está ficando imparável (Unstoppable)! 🚀",
            "Mente afiada como lâmina (Blade-sharp mind)! 🔪🧠",
            "Reflexo matemático em alta (Math reflex boosted)! ⚡",
            "Foco absoluto (Total focus)! 🎯",
            "Você está treinando como um profissional (Training like a pro)! 🏆",
            "Seu cérebro agradece (Your brain thanks you)! 🧠💚",
            "Cálculo cada vez mais rápido (Faster every time)! ⚡",
            "Você está construindo habilidade real (Building real skill)! 🏗️",
            "Inteligência em crescimento (Growing intelligence)! 🌱",
            "Isso é progresso de verdade (Real progress)! 📊",
            "Excelente consistência (Excellent consistency)! 🔁",
            "Seu treino está funcionando (Your training is working)! 🔥",
            "Matemática no modo guerreiro (Math warrior mode)! ⚔️"
        )

        Toast.makeText(this, frases.random(), Toast.LENGTH_SHORT).show()
    }

    private fun pedirNomeJogador() {
        val input = EditText(this)
        input.hint = "Digite seu nome (Enter your name)"
        // Limita o nome a 12 caracteres
        input.filters = arrayOf(android.text.InputFilter.LengthFilter(12))

        AlertDialog.Builder(this)
            .setTitle("Bem-vindo (Welcome)!")
            .setMessage("Digite seu nome para o ranking (Enter your name for ranking):")
            .setView(input)
            .setCancelable(false)
            .setPositiveButton("OK") { dialog, _ ->
                val nome = input.text.toString().trim()
                playerName = if (nome.isEmpty()) "Jogador(Player)" else nome
                dialog.dismiss()
                gerarNovaPergunta()
                atualizarPlacar()
            }
            .show()
    }

    private fun escolherFase(plays: Int, nivel: Int): Pair<LongRange, Int> {
        val range = when (nivel) {
            in 1..2 -> 0L..9L
            in 3..5 -> 0L..99L
            in 6..10 -> 0L..99L
            in 11..20 -> 0L..999L
            in 21..40 -> 0L..999L
            else -> 0L..9999L
        }


        val complexidade = when (nivel) {
            in 1..2 -> 1        // apenas +
            in 3..5 -> 2        // + e -
            in 6..10 -> 3       // +, -, ×, ÷
            in 11..20 -> 4      // expressões com parênteses ( )
            in 21..40 -> 5      // colchetes [ ]
            else -> 6           // chaves { }
        }

        return range to complexidade
    }

    private fun gerarNovaPergunta() {
        val (range, complexidade) = escolherFase(plays, nivel)
        val expressao = gerarExpressao(range, nivel)
        val resultado = avaliarExpressao(expressao)

        txtPergunta.text = "Pergunta(Question) ${plays + 1} \nNível(Level) $nivel\n\n$expressao = ?"
        respostaCorreta = resultado
        edtResposta.text.clear()
    }

    // -------------------- NOVA FUNÇÃO: rangePorOperador --------------------
    private fun rangePorOperador(op: String, nivel: Int): LongRange {
        return when (op) {
            "+" -> when (nivel) {
                in 1..3 -> 0L..9L
                in 4..6 -> 0L..20L
                in 7..10 -> 0L..50L
                else -> 0L..99L
            }

            "-" -> when (nivel) {
                in 1..5 -> 0L..9L
                in 6..10 -> 0L..20L
                in 11..20 -> 0L..50L
                else -> 0L..99L
            }

            "×" -> when (nivel) {
                in 1..8 -> 0L..9L
                in 9..20 -> 0L..20L
                else -> 0L..50L
            }

            "÷" -> when (nivel) {
                in 1..12 -> 1L..9L        // divisão simples no começo
                else -> 1L..20L
            }

            else -> 0L..9L
        }
    }

    // -------------------- SUBSTITUIÇÃO da gerarExpressao --------------------
    private fun gerarExpressao(range: LongRange, nivel: Int): String {
        // Geramos números por operador, não pelo range geral.
        // Isso garante que quando um novo operador aparece (ex: -)
        // seus operandos comecem em ranges pequenos apropriados.
        val opsSimples = when (nivel) {
            in 1..2 -> listOf("+")
            in 3..5 -> listOf("+", "-")
            in 6..10 -> listOf("+", "-", "×", "÷")
            else -> listOf("+", "-", "×", "÷")
        }

        // Decide se vai gerar composta ou simples
        val chanceComposta = when (nivel) {
            in 1..6 -> 0.0       // níveis 1-6: só simples
            in 7..20 -> 0.3      // 30% composta
            in 21..40 -> 0.5     // 50% composta
            in 41..60 -> 0.7     // 70% composta
            in 61..80 -> 0.85    // 85% composta
            else -> 0.95         // 95% composta
        }

        val gerarComposta = Math.random() < chanceComposta

        if (!gerarComposta) {
            // Expressão simples — escolhe operador conforme nível
            val op = opsSimples.random()
            val rangeOp = rangePorOperador(op, nivel)

            val a = rand.nextLong(rangeOp.first, rangeOp.last + 1)
            var b = rand.nextLong(rangeOp.first, rangeOp.last + 1)

            val divisor = if (op == "÷") b.coerceAtLeast(1) else b // garante divisor >= 1
            return "$a $op $divisor"
        } else {
            // Expressão composta: operações aleatórias
            val opsCompostas = listOf("+", "-", "×", "÷")

            return when {
                nivel in 7..20 -> {
                    val op1 = opsCompostas.random()
                    val op2 = opsCompostas.random()

                    val rangeA = rangePorOperador(op1, nivel)
                    val rangeB = rangePorOperador(op1, nivel) // b é usado com op1
                    val rangeC = rangePorOperador(op2, nivel) // c é usado com op2

                    val aC = rand.nextLong(rangeA.first, rangeA.last + 1)
                    var bC = rand.nextLong(rangeB.first, rangeB.last + 1)
                    var cC = rand.nextLong(rangeC.first, rangeC.last + 1)

                    if (op1 == "÷") bC = bC.coerceAtLeast(1)
                    if (op2 == "÷") cC = cC.coerceAtLeast(1)

                    "($aC $op1 $bC) $op2 $cC"
                }

                nivel in 21..40 -> {
                    val op1 = opsCompostas.random()
                    val op2 = opsCompostas.random()
                    val op3 = opsCompostas.random()

                    // a op1 ( b op2 c )  -> b e c dependem de op2; a depende de op1; d depende de op3
                    val rangeA = rangePorOperador(op1, nivel)
                    val rangeB = rangePorOperador(op2, nivel)
                    val rangeC = rangePorOperador(op2, nivel)
                    val rangeD = rangePorOperador(op3, nivel)

                    val aC = rand.nextLong(rangeA.first, rangeA.last + 1)
                    var bC = rand.nextLong(rangeB.first, rangeB.last + 1)
                    var cC = rand.nextLong(rangeC.first, rangeC.last + 1)
                    var dC = rand.nextLong(rangeD.first, rangeD.last + 1).coerceAtLeast(1)

                    if (op2 == "÷") { bC = bC.coerceAtLeast(1); cC = cC.coerceAtLeast(1) }
                    if (op3 == "÷") dC = dC.coerceAtLeast(1)

                    "[${aC} $op1 (${bC} $op2 ${cC})] $op3 ${dC}"
                }

                else -> {
                    // Nível muito alto — 4 operadores / 4 números
                    val op1 = opsCompostas.random()
                    val op2 = opsCompostas.random()
                    val op3 = opsCompostas.random()
                    val op4 = opsCompostas.random() // gerado mas não usado diretamente na string (mantive para variedade)

                    // Estrutura: {[ (a op1 b) op2 c ] op3 d }
                    // Portanto:
                    // - a e b são ligados a op1
                    // - c é ligado a op2
                    // - d é ligado a op3
                    val rangeA = rangePorOperador(op1, nivel)
                    val rangeB = rangePorOperador(op1, nivel)
                    val rangeC = rangePorOperador(op2, nivel)
                    val rangeD = rangePorOperador(op3, nivel)

                    val aC = rand.nextLong(rangeA.first, rangeA.last + 1)
                    var bC = rand.nextLong(rangeB.first, rangeB.last + 1)
                    var cC = rand.nextLong(rangeC.first, rangeC.last + 1)
                    var dC = rand.nextLong(rangeD.first, rangeD.last + 1)

                    if (op1 == "÷") bC = bC.coerceAtLeast(1)
                    if (op2 == "÷") cC = cC.coerceAtLeast(1)
                    if (op3 == "÷") dC = dC.coerceAtLeast(1)

                    "{[(${aC} $op1 ${bC}) $op2 ${cC}] $op3 ${dC}}"
                }
            }
        }
    }

    private fun avaliarExpressao(expr: String): Double {
        // Substitui símbolos por operadores que exp4j entende
        val expressaoFormatada = expr
            .replace("×", "*")
            .replace("÷", "/")
            .replace("[", "(")
            .replace("]", ")")
            .replace("{", "(")
            .replace("}", ")")

        return try {
            val resultado = ExpressionBuilder(expressaoFormatada).build().evaluate()

            // ✅ Trunca o resultado para 2 casas decimais (sem arredondar)
            if (resultado >= 0)
                kotlin.math.floor(resultado * 100) / 100
            else
                kotlin.math.ceil(resultado * 100) / 100

        } catch (e: Exception) {
            0.0 // Retorna 0 caso a expressão seja inválida
        }
    }

    private fun atualizarPlacar() {
        txtScore.text = "Pontuação (Score): $score | Lvl: $nivel"
        txtErros.text = "Erros (Errors): $erros / 3"
        txtRecorde.text = "Recorde (Record): $recorde"
        if (score > recorde) recorde = score
    }

    // Inicializa players de efeitos e playlist ambiente
    private fun initAmbientPlaylist() {

        // inicializa e toca a primeira música ambiente
        ambientIndex = 0
        ambientPlayer = MediaPlayer.create(this, ambientList[ambientIndex])
        ambientPlayer?.isLooping = false // looparemos por playlist, não no mesmo arquivo
        ambientPlayer?.setOnCompletionListener {
            playNextAmbient()
        }
        ambientPlayer?.start()
    }

    // Avança para a próxima música da playlist (ordem fixa)
    private fun playNextAmbient() {
        try {
            ambientPlayer?.reset()
            ambientPlayer?.release()
        } catch (e: Exception) { /* ignore */ }

        ambientIndex = (ambientIndex + 1) % ambientList.size
        ambientPlayer = MediaPlayer.create(this, ambientList[ambientIndex])
        ambientPlayer?.isLooping = false
        ambientPlayer?.setOnCompletionListener { playNextAmbient() }
        ambientPlayer?.start()
    }

    // ---- NOVAS FUNÇÕES DE EFEITOS ----
    private fun playError() {
        soundPool.play(sfxErrorId, 1f, 1f, 0, 0, 1f)
    }

    private fun playLevelUp() {
        soundPool.play(sfxLevelUpId, 1f, 1f, 0, 0, 1f)
    }

    private fun playEndSong() {
        stopAmbient() // para música ambiente
        soundPool.play(sfxEndId, 1f, 1f, 0, 0, 1f)
    }

    // Para música ambiente (chamar quando quiser pausar)
    private fun stopAmbient() {
        try {
            ambientPlayer?.stop()
            ambientPlayer?.reset()
            ambientPlayer?.release()
        } catch (e: Exception) {}
        ambientPlayer = null
    }

    private fun carregarRecordeEAtualizarUI() {
        val ranking = carregarRanking()
        recorde = ranking.maxOfOrNull { it.second } ?: 0
        txtRecorde.text = "Recorde (Record): $recorde"
    }

    private fun salvarRanking() {
        val prefs = getSharedPreferences(prefsName, Context.MODE_PRIVATE)
        val ranking = carregarRanking().toMutableList()
        val index = ranking.indexOfFirst { it.first == playerName }

        if (index >= 0) {
            // Atualiza o score e o nível se o novo for melhor
            val (_, oldScore, oldLvl) = ranking[index]
            if (score > oldScore) {
                ranking[index] = Triple(playerName, score, nivel)
            } else {
                ranking[index] = Triple(playerName, oldScore, oldLvl)
            }
        } else {
            // Adiciona novo jogador ao ranking
            ranking.add(Triple(playerName, score, nivel))
        }

        val rankingOrdenado = ranking.sortedByDescending { it.second }.take(5)
        val rankingStr = rankingOrdenado.joinToString(";") { "${it.first}:${it.second}:${it.third}" }
        prefs.edit().putString(keyRecordes, rankingStr).apply()
    }


    // Agora também carrega o nível salvo no ranking
    private fun carregarRanking(): List<Triple<String, Int, Int>> {
        val prefs = getSharedPreferences(prefsName, Context.MODE_PRIVATE)
        val rankingStr = prefs.getString(keyRecordes, "") ?: ""
        if (rankingStr.isEmpty()) return emptyList()
        return rankingStr.split(";").mapNotNull {
            val parts = it.split(":")
            if (parts.size >= 3) {
                val nome = parts[0]
                val pontos = parts[1].toIntOrNull() ?: 0
                val nivel = parts[2].toIntOrNull() ?: 1
                Triple(nome, pontos, nivel)
            } else null
        }
    }


    private fun mostrarRankingDialog() {
        salvarRanking()
        val ranking = carregarRanking()

        val textoRanking = StringBuilder()
        textoRanking.append("3 erros (3 mistakes).\nSua pontuação(Your score): $score | Lvl: $nivel\n\n\nRanking:\n\n")
        ranking.forEachIndexed { i, (nome, pts, lvl) ->
            textoRanking.append("${i + 1}. $nome - $pts pontos(points) | Lvl $lvl\n\n")
        }

        AlertDialog.Builder(this)
            .setTitle("Fim de jogo (Game Over)!")
            .setMessage(textoRanking.toString())
            .setCancelable(false)
            .setPositiveButton("Continuar (Continue)") { dialog, _ ->
                // Reinicia as variáveis do jogo
                score = 0
                erros = 0
                plays = 0
                progresso = 0
                nivel = 1

                // Atualiza barra de progresso e textos
                progressoNivel.progress = 0
                atualizarNivel()
                atualizarPlacar()
                gerarNovaPergunta()

                dialog.dismiss()
            }
            .setNegativeButton("Sair (Exit)") { dialog, _ ->
                dialog.dismiss()
                finish()
            }
            .setNeutralButton("Compartilhar (Share)") { dialog, _ ->
                val mensagem = """
        Meus resultados no Math Quest (My results in Math Quest):

        Nível (Level): $nivel
        Pontos (Points): $score
        Acertos (Hits): ${plays - erros}

        Desafie-se também (Try it yourself)!
        https://play.google.com/store/apps/details?id=com.alex.mathquest
    """.trimIndent()

                val intent = android.content.Intent().apply {
                    action = android.content.Intent.ACTION_SEND
                    putExtra(android.content.Intent.EXTRA_TEXT, mensagem)
                    type = "text/plain"
                }

                val chooser = android.content.Intent.createChooser(intent, "Compartilhar via (Share via)")
                startActivity(chooser)

                // --- Agora reiniciamos o jogo como se o usuário tivesse tocado em "Continuar" ---
                score = 0
                erros = 0
                plays = 0
                progresso = 0
                nivel = 1

                progressoNivel.progress = 0
                atualizarNivel()
                atualizarPlacar()
                gerarNovaPergunta()

                // Fecha o diálogo
                dialog.dismiss()
            }
            .show()


    }

    override fun onDestroy() {
        super.onDestroy()
        ambientPlayer?.release()
        soundPool.release() // libera todos os efeitos curtos
    }
}
