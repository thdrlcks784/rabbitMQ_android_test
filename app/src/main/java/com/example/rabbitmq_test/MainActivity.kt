package com.example.rabbitmq_test

import android.os.Bundle
import android.os.Handler
import android.os.Message
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.rabbitmq.client.*
import java.io.IOException
import java.nio.charset.Charset
import java.util.concurrent.TimeoutException
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity(){
    private var topic = "hello"
    private var thread: Thread? = null
    private val factory = ConnectionFactory()
    private var connection: Connection? = null
    private var channel: Channel? = null
    private var lastDelivery: Delivery? = null

    var handler = ValueHandler()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        initRabbitMQConn()

        findViewById<Button>(R.id.resBt).setOnClickListener {
            topic = findViewById<EditText>(R.id.exchange).text.toString()
            listenMessageMQ();
        }

        findViewById<Button>(R.id.sendBt).setOnClickListener {
            sendMessageMQ(findViewById<EditText>(R.id.message).text.toString())
        }

        findViewById<Button>(R.id.allAck).setOnClickListener {
            if (lastDelivery != null) {
                thread {
                    channel?.basicAck(lastDelivery!!.getEnvelope().getDeliveryTag(), true)
                }
            }
        }
    }


    private fun initRabbitMQConn() {
        thread {
            factory.host = "10.0.69.18"
            factory.port = 5672
            factory.username = "guest"
            factory.password = "guest"
            connection = factory.newConnection()
            channel = connection!!.createChannel()
        }
    }

    private fun listenMessageMQ() {
        thread {
            channel!!.exchangeDeclare(topic, "fanout", false, false, null)
            var queue = channel!!.queueDeclare(findViewById<EditText>(R.id.queuename).text.toString(), true, false, false, null).queue
            println(" [*] Waiting for messages. To exit press CTRL+C")

            channel!!.queueBind(findViewById<EditText>(R.id.queuename).text.toString(), topic, "")
            val deliverCallback = DeliverCallback { consumerTag: String?, delivery: Delivery ->
                val message = String(delivery.body, Charset.defaultCharset())
                println(" 수신데이터::::: '$message'")
                val msg = handler.obtainMessage()
                val bundle = Bundle()
                bundle.putString("message", message)
                msg.data = bundle
                handler.sendMessage(msg)
                lastDelivery = delivery
            }
            channel!!.basicConsume(
                queue, false, deliverCallback
            ) { consumerTag: String? -> }
        }
    }



    private fun sendMessageMQ(message: String) {
        thread = thread {
            try {
                factory.newConnection().use { connection ->
                    connection.createChannel().use { channel ->
                        channel.exchangeDeclare(topic, "fanout", false, false, null)
                        channel.basicPublish(topic, "", null, message.toByteArray())
                        println(" [x] Set '$message'")
                    }
                }
            } catch (e: TimeoutException) {
                e.printStackTrace()
            } catch (e: IOException) {
                e.printStackTrace()
            } catch (e: InterruptedException) {
                e.printStackTrace()
            }
        }
    }

    inner class ValueHandler : Handler() {
        override fun handleMessage(msg: Message) {
            super.handleMessage(msg)
            val bundle: Bundle = msg.data
            val value = bundle.getString("message")
            findViewById<TextView>(R.id.res).text = "${findViewById<TextView>(R.id.res).text}\n$value"
        }
    }
}