package ai.enginery.module

import com.google.gson.Gson
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okio.internal.commonAsUtf8ToByteArray
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.*
import javax.sound.sampled.*


class WebsocketCommunication {

    val audioBuffer = Channel<ByteArray>(capacity = Channel.UNLIMITED)
    val outputBuffer = mutableListOf<ByteArray>()

    val mutex = Mutex()
    var savedSession: DefaultWebSocketSession? = null // Uložená session

    fun create(url: String) {




        GlobalScope.launch {
            println("Starting")
            val client = HttpClient(CIO) {
                println("GOGOG")
                install(WebSockets){
                    println("COCOC")
                }
            }
            println("POPO")

            client.webSocket(url) { // Váš WebSocket server
                println("OPENED CONNECTION0")
                mutex.withLock {
                    savedSession = this // Uložení aktuální WebSocket session
                }




              //    send(Frame.Text("{ \"event\": \"clear\"}"))
               send(Frame.Text("{\"type\":\"conversation_initiation_client_data\",\"conversation_config_override\":{\"agent\":{},\"tts\":{}}}"))
                try {
                    for (frame in incoming) {
                        when (frame) {
                            is Frame.Text -> {
                               // println("Přijatá zpráva: ${frame.readText()}")
                                onMessageReceived(this, frame.readText())
                            }

                            is Frame.Binary -> println("Přijatá binární data")
                            is Frame.Close -> println("Close messsge" + frame.readBytes())

                            else -> println("Nepodporovaný typ zprávy")
                        }
                    }

                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    mutex.withLock {
                        if (savedSession == this) savedSession = null // Odstranění session při ukončení
                    }
                }
                println("CLOSED CONNECTION0")

            }

            println("CLOSED CONNECTION")


            client.close()

        }


    }

    fun onMessageReceived(session: DefaultClientWebSocketSession, text: String) {
        val json = text
        val gson = Gson()
        val resultMap: Map<String, Any> = gson.fromJson(json, Map::class.java) as Map<String, Any>

        when (resultMap["type"]) {
            "ping" -> sendPong(session, ((resultMap["ping_event"] as Map<String, Any>)["event_id"] as Double).toInt())
            "conversation_initiation_metadata" -> {}
            "audio" -> getAudio(resultMap["audio_event"] as Map<String, Any>)
            "agent_response" -> {}
            "user_transcript" -> {}
        }

    }

    private  fun getAudio(audio: Map<String, Any>) {
        val audioEventId = (audio["event_id"] as Double).toInt()


        val decodedBytes = Base64.getDecoder().decode(audio["audio_base_64"].toString())
        println("audio: " + audioEventId + " " + decodedBytes.size + " bytes")
        // println(decodedBytes.toList())

        val sampleRate = 16000 // Hz
        val numChannels = 1 // Mono
        val bitsPerSample = 16 // 16-bitové vzorky

        val sample = createSample(decodedBytes, sampleRate, numChannels, bitsPerSample)
        GlobalScope.launch {
            audioBuffer.send(decodedBytes)
        }

        //sampleBuffer.add(sample)
    }

    fun saveBase64ToFile(decodedBytes: ByteArray, outputFilePath: String): Boolean {
        return try {
            // Dekódování Base64 řetězce na byte array


            //   File(outputFilePath).writeBytes(decodedBytes)


            println("Zvukový soubor byl úspěšně uložen do: $outputFilePath")

            // val sound = Gdx.audio.newSound(Gdx.files.internal("record.wav"))
            // sound.play()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            println("Chyba při ukládání souboru.")
            false
        }
    }


    fun sendAudio(audio: String) {



        val message = "{\"user_audio_chunk"+ "\":\"${audio}\"}"

        val gson = Gson()
        val result: Map<String, Any> = gson.fromJson(message, Map::class.java) as Map<String, Any>
        val decoded = Base64.getDecoder().decode(result["user_audio_chunk"].toString())

    //
        //
        //  listen(decoded)

        sendSessionMessage(message)
        println("SENT!: " + message)
        //println("SENT!: " + audio.toList())

    }

    fun fixPadding(base64: String): String {
        var base64 = base64
        val paddingNeeded = 4 - (base64.length % 4)
        if (paddingNeeded < 4) {
            base64 += "=".repeat(paddingNeeded)
        }
        println("FIXED")
        return base64
    }



    fun sendPong(session: DefaultClientWebSocketSession, eventId: Int) {
        val map = mutableMapOf<String, Any>()
        map.put("type", "pong")
        map.put("event_id", eventId)

        val gson = Gson()
        val json = gson.toJson(map)

        sendSessionMessage(json)
        println("Odeslán pong: " + eventId)

    }



    private fun shortArrayToByteArray(shortArray: ShortArray): ByteArray {
        val byteArray = ByteArray(shortArray.size * 2)
        val buffer = ByteBuffer.wrap(byteArray).order(ByteOrder.LITTLE_ENDIAN)
        for (value in shortArray) {
            buffer.putShort(value)
        }
        return byteArray
    }

    private fun intToByteArray(value: Int): ByteArray {
        return ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(value).array()
    }

    private fun shortToByteArray(value: Short): ByteArray {
        return ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN).putShort(value).array()
    }



    fun sendSessionMessage(message: String) {
        runBlocking {
            mutex.withLock {
                savedSession?.send(Frame.Text(true, message.commonAsUtf8ToByteArray())) ?: println("No active WebSocket session")
            }
        }
    }



    fun fillOutputBuffer(audioBuffer: ByteArray) {
        outputBuffer.add(audioBuffer)
    }

    fun createSample(
        audioBuffer: ByteArray,
        sampleRate: Int,
        numChannels: Int,
        bitsPerSample: Int,
    ): ByteArray {
      //  println("FINAL AUDIO LENGTH: " + audioBuffer.size)
        val fos: MutableList<ByteArray> = mutableListOf()
        val audioData = audioBuffer

        // Vypočet velikostí
        val byteRate = sampleRate * numChannels * bitsPerSample / 8
        val subchunk2Size = audioData.size
        val chunkSize = 36 + subchunk2Size

        // WAV hlavička
        fos.add("RIFF".toByteArray()) // ChunkID
        fos.add(intToByteArray(chunkSize)) // ChunkSize
        fos.add("WAVE".toByteArray()) // Format

        // fmt chunk
        fos.add("fmt ".toByteArray()) // Subchunk1ID
        fos.add(intToByteArray(16)) // Subchunk1Size (16 pro PCM)
        fos.add(shortToByteArray(1.toShort())) // AudioFormat (1 = PCM)
        fos.add(shortToByteArray(numChannels.toShort())) // NumChannels
        fos.add(intToByteArray(sampleRate)) // SampleRate
        fos.add(intToByteArray(byteRate)) // ByteRate
        fos.add(shortToByteArray((numChannels * bitsPerSample / 8).toShort())) // BlockAlign
        fos.add(shortToByteArray(bitsPerSample.toShort())) // BitsPerSample

        // data chunk
        fos.add("data".toByteArray()) // Subchunk2ID
        fos.add(intToByteArray(subchunk2Size)) // Subchunk2Size
        fos.add(audioData) // Zvuková data

        return fos.flatMap { it.toList() }.toByteArray()
    }

    suspend fun playAudioStream(byteArray: ByteArray) {
        try {
            // Vytvoření InputStream z byte array
            val audioStream: InputStream = ByteArrayInputStream(byteArray)

            // Načtení audio streamu jako AudioInputStream
            val audioInputStream: AudioInputStream = withContext(Dispatchers.IO) {
                AudioSystem.getAudioInputStream(audioStream)
            }

            // Získání formátu zvukových dat
            val format: AudioFormat = audioInputStream.format

            // Otevření SourceDataLine podle formátu
            val info = DataLine.Info(SourceDataLine::class.java, format)
            val line = AudioSystem.getLine(info) as SourceDataLine

            line.open(format)
            println("Přehrávání spuštěno.")
            line.start()

            // Buffer pro postupné přehrávání zvuku
            val bufferSize = 4096
            val buffer = ByteArray(bufferSize)

            // Čtení a přehrávání dat
            var bytesRead: Int
            while (audioInputStream.read(buffer, 0, buffer.size).also { bytesRead = it } != -1) {
                line.write(buffer, 0, bytesRead)
            }

            // Dokončení přehrávání
            line.drain()
            line.stop()
            line.close()

            audioInputStream.close()
            audioStream.close()

            println("Přehrávání dokončeno.")

        } catch (e: Exception) {
            e.printStackTrace()
            println("Chyba při přehrávání zvuku: ${e.message}")
        }
    }


    suspend fun playAudio(byteArray: ByteArray) {
        try {
            val audioStream = ByteArrayInputStream(byteArray)
            val audioInputStream: AudioInputStream = withContext(Dispatchers.IO) {
                AudioSystem.getAudioInputStream(audioStream)
            }

            val clip: Clip = AudioSystem.getClip().apply {
                withContext(Dispatchers.IO) {
                    open(audioInputStream)
                }
            }

            // Přehrání zvuku
            clip.start()
            println("Zvuk se přehrává...")

            withContext(Dispatchers.IO) {
                while (clip.isRunning) {
                    println("Aktuální pozice: ${clip.microsecondPosition} / ${clip.microsecondLength}")
                    delay(100)  // Delší zpoždění pro snížení zátěže
                }
            }

            println("Zvuk dohrál.")

            // Uklid
            clip.close()
            audioInputStream.close()
            audioStream.close()

        } catch (e: Exception) {
            e.printStackTrace()
            println("Chyba při přehrávání zvuku: ${e.message}")
        }
    }
    private fun listen(audio: ByteArray) {
        val sampleRate = 16000 // Hz
        val numChannels = 1 // Mono
        val bitsPerSample = 16 // 16-bitové vzorky

        // val sample = createSample(audio, sampleRate, numChannels, bitsPerSample)
        GlobalScope.launch {
            //    playAudio(sample)
        }
    }

}
