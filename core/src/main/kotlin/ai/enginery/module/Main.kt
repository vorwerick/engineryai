package ai.enginery.module

import ai.enginery.module.screens.IntroScreen
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.InputProcessor
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.math.Vector2
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import ktx.app.KtxGame
import ktx.app.KtxScreen
import ktx.app.clearScreen
import ktx.assets.disposeSafely
import ktx.async.KtxAsync
import ktx.graphics.copy
import ktx.graphics.use
import java.io.*
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean
import javax.sound.sampled.*
import kotlin.experimental.and
import kotlin.math.max
import kotlin.math.min
import kotlin.system.exitProcess


class Main(val windowed: Boolean) : KtxGame<KtxScreen>() {

    override fun create() {
        KtxAsync.initiate()

        addScreen(IntroScreen(this))
        //Gdx.graphics.setWindowedMode(Gdx.graphics.displayMode.width, Gdx.graphics.displayMode.height);
        println("RESOLUTION: "+ Gdx.graphics.displayMode.width +"x"+Gdx.graphics.displayMode.height)
        if(!windowed){
            Gdx.graphics.setFullscreenMode(Gdx.graphics.displayMode)
        }
        setScreen<IntroScreen>()

    }
}

enum class RecordingState {
    IDLE, RECORDING
}

class MainScreen() : KtxScreen, InputProcessor {

    fun getnPin17State(): Int? {

        // Spustí příkaz `cat /proc/cpuinfo` pro zjištění informací o CPU
        val processBuilder = ProcessBuilder("gpioget", "gpiochip0", "17")


        // Přesměrování chybového výstupu do standardního výstupu
        processBuilder.redirectErrorStream(true)


        // Spuštění procesu
        val process = processBuilder.start()


        // Čtení výstupu příkazu
        val reader = BufferedReader(InputStreamReader(process.inputStream))
        var line: String
        val output: MutableList<String> = ArrayList()

        while ((reader.readLine().also { line = it }) != null) {
            output.add(line) // Uložení každého řádku do seznamu
        }


        // Počkej na dokončení příkazu a získej návratový kód
        val exitCode = process.waitFor()
        if (exitCode == 0) {

            // println("Výstup příkazu: ")
            if (output.isNotEmpty()) {
                //   println(output[0])
                return output[0].toIntOrNull()
            }

        } else {
            println("Chyba při spuštění příkazu, návratový kód: $exitCode")

        }
        return null
    }

    private val SAMPLES = 16000
    private val channel = Channel<String>()

    private var image0: Texture = Texture(Gdx.files.internal("1.jpg"))
    private var image1: Texture = Texture(Gdx.files.internal("2.jpg"))
    private var image2: Texture = Texture(Gdx.files.internal("3.jpg"))
    private var image3: Texture = Texture(Gdx.files.internal("4.jpg"))

    private var pressToTalk: Texture = Texture(Gdx.files.internal("presstotalk.png"))

    private val images = mutableListOf(image0, image1, image2, image3)
    private var currentImage = 0

    private val batch = SpriteBatch()
    private val shapeRenderer = ShapeRenderer()
    private val camera = OrthographicCamera(1920f, 1080f)

    private var isRecordingInput = false
    private var recordingState: RecordingState = RecordingState.IDLE
    private var isRecordingInputHold = 0

    private val transitionTime = 10f
    private val defaultZoom = 1f

    private var imageTransitionTimer = 0f

    private var isRecording = false

    private var format: AudioFormat? = null

    private var recordJob: Job? = null

    private val SEND_CHUNK_AFTER = 250
    private val VOLUME_MULTIPLICATOR = 13.0

    val apiUrl = "https://api.elevenlabs.io/v1/speech"  // URL API endpointu

    // Zde bys měl mít svůj API klíč (např. z dashboardu ElevenLabs)
    //val apiKey = "sk_55802ae939d869ceed698377e5693334ff11f59fd14cff05"
  //  val apiKey = "sk_cae9fb9b08d53e7496b817917b6b58d4ee83f47059a69efc"

    val apiKey = "sk_5e8edfdb99b5b2a5251082669717393724e59c44caca08b2"

    //val agentId = "L76yM1RsyQeTVwOeTyD4"
   // val agentId = "wdI2AP5lnihvrjjZ0PyD"
    val agentId = "dX6cVUlyytxBxPWRLkxo"

    var signedUrl: String? = null

    var websocketCommunication: WebsocketCommunication? = null

    val stopFlag = AtomicBoolean(false)


    init {
        GlobalScope.launch {

            while (true) {
                var state = getnPin17State()
                if (state == 1) {
                    isRecordingInput = true
                    // println("Tlačítko bylo stisknuto.")
                } else {
                    isRecordingInput = false
                    // println("Tlačítko bylo uvolněno.")
                }
                Thread.sleep(250);
            }
        }

        GlobalScope.launch(Dispatchers.IO) {
            // Získání formátu zvukových dat
            val format: AudioFormat = AudioFormat(16000f, 16, 1, true, false)

            // Otevření SourceDataLine podle formátu
            val info = DataLine.Info(SourceDataLine::class.java, format)
            val line = AudioSystem.getLine(info) as SourceDataLine

            line.open(format)
            println("Přehrávání spuštěno.")
            line.start()
            while (true) {
                try {
                    val chunk = websocketCommunication?.audioBuffer?.receive()  // Blokující čekání na nový chunk
                    line.write(chunk, 0, chunk!!.size)  // Přehrání chunku
                } catch (e: Exception) {
                    // println("Chyba při přehrávání: ${e.message}")
                }
            }
        }


        Gdx.input.inputProcessor = this

        Gdx.app.log("URL", "Signed url " + signedUrl.toString())



        format = AudioFormat(
            AudioFormat.Encoding.PCM_SIGNED,
            16000f,
            16,
            1,
            2,
            16000f,
            false
        );// 16 kHz, 16-bit mono, signed PCM


        // line = AudioSystem.getTargetDataLine(format)
        // recorder = Gdx.audio.newAudioRecorder(16000, true)

        GlobalScope.launch {
            while (signedUrl == null) {
                println("try to get")
                val result = async {
                    return@async Requests.getSignedUrl(agentId, apiKey)?.get("signed_url") as String
                }.await()
                signedUrl = result
                delay(1000)
            }
            println(signedUrl.toString())
            websocketCommunication = WebsocketCommunication()
            websocketCommunication!!.create(signedUrl!!)
            println("created")
        }

    }


    override fun show() {
        super.show()

        //line = AudioSystem.getTargetDataLine(format)


        camera.zoom = defaultZoom
        //images.shuffle()

    }

    override fun render(delta: Float) {

        if (Gdx.input.isButtonJustPressed(Input.Buttons.RIGHT)) {
            exitProcess(0)
        }


        if (!isRecordingInput) {
            isRecordingInputHold -= (delta * 1000).toInt() * 20
            if (isRecordingInputHold <= 0) {
                isRecordingInputHold = 0
                if (recordingState == RecordingState.RECORDING) {
                    recordingState = RecordingState.IDLE //stop recording
                    recordingStopped()
                }
            }
        } else {
            isRecordingInputHold += (delta * 1000).toInt() * 20
            if (isRecordingInputHold > 2000) {
                isRecordingInputHold = 2000
                if (recordingState == RecordingState.IDLE) {
                    recordingState = RecordingState.RECORDING //start recording
                    if (!isRecording) {
                        recordingStarted()
                    }
                }


            }
        }

        if (recordJob?.isCompleted == true) {
            println("RECORDING STOPPED")
            isRecording = false
            println("RECORDING OFF")

            recordJob = null
        }



        imageTransitionTimer += delta
        if (imageTransitionTimer >= transitionTime) {
            currentImage++
            camera.zoom = defaultZoom
            if (currentImage >= images.size) {
                currentImage = 0
            }

            imageTransitionTimer = 0f
        }


        clearScreen(red = 0f, green = 0f, blue = 0f)
        batch.projectionMatrix = camera.projection
        shapeRenderer.projectionMatrix = camera.projection
       // camera.zoom += delta * 0.005f

        batch.use {
            it.draw(
                images[currentImage], -(images[currentImage].width / 2f), -(images[currentImage].height / 2f)
            )

            val bottomRight = Vector2(camera.viewportWidth/2 - (pressToTalk.width*1.8f), 0f)
            it.draw(pressToTalk,camera.position.x + camera.position.x + 580, camera.position.y - (camera.viewportHeight / 2) , 300f, 50f)

        }

        shapeRenderer.use(ShapeRenderer.ShapeType.Filled) {
            shapeRenderer.color = Color.WHITE.copy(alpha = 0.5f)
            if (recordingState == RecordingState.RECORDING) {
                shapeRenderer.color = Color.GREEN
            }
            val radius = 6f + (6 * (isRecordingInputHold / 1000f))
            it.circle(camera.position.x, camera.position.y - (camera.viewportHeight / 2) + 160, radius, 64)
        }

        camera.update()
    }


    private fun recordingStopped() {

        stopFlag.set(true)


    }

    suspend fun readSamples(dataLine: TargetDataLine, bufferSize: Int): ShortArray {
        val byteBuffer = ByteArray(bufferSize) // Buffer pro čtení bajtů
        val bytesRead = dataLine.read(byteBuffer, 0, bufferSize) // Načtení dat

        // Převod na ShortArray (16bit signed PCM, little endian)
        val shortBuffer = ByteBuffer.wrap(byteBuffer)
        shortBuffer.order(ByteOrder.LITTLE_ENDIAN) // Nastavení little endian podle formátu

        val samples = ShortArray(bytesRead / 2) // 2 bajty na vzorek
        for (i in samples.indices) {
            samples[i] = shortBuffer.getShort()
        }

        return samples
    }

    private fun recordingStarted() {

        stopFlag.set(false)
        isRecording = true
        println("RECORDING STARTED")



        recordJob = CoroutineScope(Dispatchers.IO).launch {
            with(AudioSystem.getTargetDataLine(format)) {
                open(format)
                start()

                try {
                    val buffer = mutableListOf<Byte>()
                    var timestamp = System.currentTimeMillis()
                    while (!stopFlag.get()) {
                        val bytes = ByteArray(available())
                        read(bytes, 0, bytes.size)
                        buffer.addAll(bytes.asList())

                        if (System.currentTimeMillis() - timestamp > SEND_CHUNK_AFTER) {
                            timestamp = System.currentTimeMillis()
                            val amplified = amplify(buffer.toByteArray(), VOLUME_MULTIPLICATOR)
                            websocketCommunication?.sendAudio(Base64.getEncoder().encodeToString(amplified))
                            buffer.clear()
                        }
                    }

                } catch (e: IOException) {
                    e.printStackTrace()
                } finally {
                    stop()
                    close()
                }

            }


        }

    }


    fun amplifySamples(samples: ShortArray, gain: Float): ShortArray {
        return samples.map { sample ->
            // Zesílení vzorku
            val amplified = (sample * gain).toInt()

            // Oříznutí hodnoty do rozsahu -32,768 až 32,767

            amplified.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }.toShortArray()
    }

    override fun dispose() {
        image0.disposeSafely()
        image1.disposeSafely()
        image2.disposeSafely()
        image3.disposeSafely()
        batch.disposeSafely()
    }


    fun shortArrayToByteArray(shortArray: ShortArray): ByteArray {
        val byteArray = ByteArray(shortArray.size * 2) // Každý short má 2 byty
        shortArray.forEachIndexed { index, value ->
            val byteIndex = index * 2
            byteArray[byteIndex] = (value and 0xFF).toByte() // Nízký byte
            byteArray[byteIndex + 1] = ((value.toInt() shr 8) and 0xFF).toByte() // Vysoký byte
        }
        return byteArray
    }

    fun shortArrayToByteArrayBig(shortArray: ShortArray): ByteArray {
        val byteArray = ByteArray(shortArray.size * 2) // Každý short má 2 byty
        shortArray.forEachIndexed { index, value ->
            val byteIndex = index * 2
            byteArray[byteIndex + 1] = (value and 0xFF).toByte() // Nízký byte
            byteArray[byteIndex] = ((value.toInt() shr 8) and 0xFF).toByte() // Vysoký byte
        }
        return byteArray
    }

    private fun intToByteArray(value: Int): ByteArray {
        return ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(value).array()
    }

    private fun shortToByteArray(value: Short): ByteArray {
        return ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN).putShort(value).array()
    }


    private fun amplify(audioData: ByteArray, gain: Double): ByteArray {
        var i = 0
        while (i < audioData.size) {
            // Spojte dva bajty na 16bitový vzorek
            var sample = (audioData[i + 1].toInt() shl 8) or (audioData[i].toInt() and 0xFF)
            // Aplikujte zesílení
            sample = (sample * gain).toInt()
            // Ořízněte, aby nedošlo k přetečení
            sample = max(-32768.0, min(32767.0, sample.toDouble())).toInt()
            // Rozdělte zpět na bajty
            audioData[i] = (sample and 0xFF).toByte()
            audioData[i + 1] = ((sample shr 8) and 0xFF).toByte()
            i += 2
        }
        return audioData
    }

    override fun keyDown(p0: Int): Boolean {
        if (Input.Keys.ENTER == p0) {
            isRecordingInput = true
        }
        return true
    }

    override fun keyUp(p0: Int): Boolean {
        if (Input.Keys.ENTER == p0) {
            isRecordingInput = false
        }
        return true
    }

    override fun keyTyped(p0: Char): Boolean {
        return true
    }

    override fun touchDown(p0: Int, p1: Int, p2: Int, p3: Int): Boolean {
        return true
    }

    override fun touchUp(p0: Int, p1: Int, p2: Int, p3: Int): Boolean {
        return true
    }

    override fun touchCancelled(p0: Int, p1: Int, p2: Int, p3: Int): Boolean {
        return true
    }

    override fun touchDragged(p0: Int, p1: Int, p2: Int): Boolean {
        return true
    }

    override fun mouseMoved(p0: Int, p1: Int): Boolean {
        return true
    }

    override fun scrolled(p0: Float, p1: Float): Boolean {
        return true
    }


}
