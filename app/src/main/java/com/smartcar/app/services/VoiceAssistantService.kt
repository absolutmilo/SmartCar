package com.smartcar.app.services

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.util.Log
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import com.smartcar.app.R
import java.util.Locale
import android.os.Bundle
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothAdapter
import androidx.annotation.RequiresPermission
import androidx.core.content.ContextCompat




class VoiceAssistantService : Service(), TextToSpeech.OnInitListener {

    private var speechRecognizer: SpeechRecognizer? = null
    private var recognizerIntent: Intent? = null
    private var textToSpeech: TextToSpeech? = null

    private val CHANNEL_ID = "SmartCarVoiceServiceChannel"
    private val TAG = "VoiceAssistantService"

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Servicio creado")
        createNotificationChannel()
        startForegroundServiceNotification()

        textToSpeech = TextToSpeech(this, this)

        if (SpeechRecognizer.isRecognitionAvailable(this)) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
            setupSpeechRecognizer()
        } else {
            Toast.makeText(this, "El reconocimiento de voz no está disponible", Toast.LENGTH_SHORT).show()
            stopSelf()
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            textToSpeech?.language = Locale("es", "ES")
            speak("Asistente SmartCar activado")
        }
    }

    @RequiresPermission(allOf = [Manifest.permission.BLUETOOTH_CONNECT])
    private fun toggleBluetooth(enable: Boolean): String {
        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        val bluetoothAdapter = bluetoothManager?.adapter

        if (bluetoothAdapter == null) {
            return toneResponse("Tu dispositivo no soporta Bluetooth.")
        }

        val hasPermission = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.BLUETOOTH_CONNECT
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasPermission) {
            return toneResponse("No tengo permisos para controlar el Bluetooth. Concede el permiso y vuelve a intentarlo.")
        }

        return try {
            val isEnabled = bluetoothAdapter.isEnabled
            when {
                enable && !isEnabled -> {
                    val enableIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    startActivity(enableIntent)
                    toneResponse("Por favor, activa el Bluetooth desde la ventana del sistema.")
                }

                !enable && isEnabled -> {
                    bluetoothAdapter.disable()
                    toneResponse("He desactivado el Bluetooth del dispositivo.")
                }

                else -> {
                    val msg = if (isEnabled)
                        "El Bluetooth ya está activado."
                    else
                        "El Bluetooth ya está desactivado."
                    toneResponse(msg)
                }
            }
        } catch (e: SecurityException) {
            toneResponse("No tengo permisos suficientes para cambiar el estado del Bluetooth.")
        }
    }

    private fun toneResponse(message: String): String {
        println("SmartCar dice: $message")
        return message
    }

    private fun setupSpeechRecognizer() {
        recognizerIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-ES")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }

        speechRecognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                Log.d(TAG, "Listo para escuchar")
            }

            override fun onBeginningOfSpeech() {
                Log.d(TAG, "Comenzando a escuchar...")
            }

            override fun onRmsChanged(rmsdB: Float) {}

            override fun onBufferReceived(buffer: ByteArray?) {}

            override fun onEndOfSpeech() {
                Log.d(TAG, "Fin de la entrada de voz")
            }

            override fun onError(error: Int) {
                Log.e(TAG, "Error en reconocimiento: $error")
                restartListeningWithDelay()
            }

            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                matches?.firstOrNull()?.let { processCommand(it.lowercase(Locale.getDefault())) }
                restartListeningWithDelay()
            }

            override fun onPartialResults(partialResults: Bundle?) {}

            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        startListening()
    }

    private fun startListening() {
        if (ActivityCompat.checkSelfPermission(
                this, Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            speechRecognizer?.startListening(recognizerIntent)
            Log.d(TAG, "Escuchando comandos...")
        } else {
            Log.e(TAG, "Permiso de micrófono no concedido")
        }
    }

    private fun restartListeningWithDelay() {
        speechRecognizer?.cancel()
        speechRecognizer?.stopListening()
        Thread.sleep(1000)
        startListening()
    }

    private fun processCommand(command: String) {
        Log.d(TAG, "Comando detectado: $command")

        when {
            command.contains("kilometraje") || command.contains("actualizar kilometraje") ->
                speak("Por favor, indícame el kilometraje actual del vehículo.")

            command.contains("marca") || command.contains("modelo") ->
                speak("¿Cuál es la marca y modelo de tu vehículo?")

            command.contains("soat") ->
                speak("Tu SOAT vence el 12 de diciembre. Te avisaré con antelación.")

            command.contains("tecnicomecánica") ->
                speak("Tu revisión técnico-mecánica vence el 15 de enero.")

            command.contains("encender bluetooth") ->
                speak("Encendiendo Bluetooth...")

            command.contains("apagar bluetooth") ->
                speak("Apagando Bluetooth...")

            command.contains("placa") ->
                speak("La placa registrada es ABC-123. ¿Deseas actualizarla?")

            command.contains("asistente") || command.contains("gemini") ->
                speak("Abriendo el asistente Gemini en tu dispositivo.")

            else -> speak("No he entendido el comando. Por favor, repite.")
        }
    }

    private fun speak(message: String) {
        textToSpeech?.speak(message, TextToSpeech.QUEUE_FLUSH, null, null)
        Log.d(TAG, "TTS: $message")
    }

    private fun startForegroundServiceNotification() {
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("SmartCar escuchando")
            .setContentText("El asistente de voz está activo.")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .build()

        startForeground(1, notification)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Canal SmartCar Voice Service",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        speechRecognizer?.destroy()
        textToSpeech?.shutdown()
        Log.d(TAG, "Servicio destruido")
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
