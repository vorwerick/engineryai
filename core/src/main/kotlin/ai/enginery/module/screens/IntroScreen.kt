package ai.enginery.module.screens

import ai.enginery.module.Main
import ai.enginery.module.MainScreen
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.math.Vector2
import ktx.app.KtxScreen
import ktx.graphics.center

class IntroScreen(val main: Main) : KtxScreen {
    private var logo: Texture = Texture(Gdx.files.internal("enginery.png"))
    var timer = 5000f
    val spriteBatch = SpriteBatch()
    var off = false
    private val camera = OrthographicCamera(Gdx.graphics.displayMode.width.toFloat(), Gdx.graphics.displayMode.height.toFloat())


    override fun show() {
        super.show()
        println("RES: "+ Gdx.graphics.width +"x"+Gdx.graphics.height)

    }

    override fun render(delta: Float) {
        super.render(delta)
        timer -= delta * 1000

        if (timer <= 0 && !off) {
            off = true
            timer = 0f

            main.addScreen(MainScreen())
            main.setScreen<MainScreen>()
        }
        val center = camera.position.cpy()
        spriteBatch.projectionMatrix = camera.combined
        spriteBatch.begin()
        spriteBatch.draw(logo, center.x-(logo.width/2), center.y-(logo.height/2), 798f, 142f)
        spriteBatch.end()
        Gdx.gl.glClearColor(0f, 0f, 0f, 1f)
    }
}
