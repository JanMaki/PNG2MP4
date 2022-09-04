package com.project_rating.png_to_mp4

import com.dropbox.core.BadRequestException
import com.dropbox.core.DbxRequestConfig
import com.dropbox.core.v2.DbxClientV2
import com.dropbox.core.v2.files.WriteMode
import org.jcodec.api.SequenceEncoder
import org.jcodec.common.model.ColorSpace
import org.jcodec.scale.AWTUtil
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.file.Paths
import javax.imageio.ImageIO

fun main(args: Array<String>) {
    //引数があるかを確認
    if (args.isEmpty()){
        println("第１引数にDropBoxのTokenを入力してください")
        return
    }

    PNG2MP4(args[0])
}

class PNG2MP4(token: String) {
    init {

        //jarがあるディレクトリ
        val parentDirectory: File =
            File(Paths.get(javaClass.protectionDomain.codeSource.location.toURI()).toString()).parentFile

        //DropBoxにログイン
        val config: DbxRequestConfig = DbxRequestConfig.newBuilder("png2mp4").build()
        val client = DbxClientV2(config, token)

        //キャッシュのディレクトリ
        val cacheDirectory = File(parentDirectory, "cache")
        //ディレクトリがあるかを確認
        if (!cacheDirectory.exists()) {
            //ディレクトリ作成
            cacheDirectory.mkdirs()
        }

        //変換開始
        println("変換を開始")
        try {
            client.files().listFolder("").entries.forEach {
                //pngかを確認
                if (!it.name.endsWith("png")) return@forEach

                //pngのFile
                val imageFile = File(cacheDirectory, it.name)

                //ダウンロード
                println("${it.name}をダウンロード")
                var outputStream: FileOutputStream? = null
                try {
                    //ストリームを作成
                    outputStream = FileOutputStream(imageFile).apply {
                        //ダウンロード
                        client.files().download("/${it.name}").download(this)
                    }
                } catch (ignore: Exception) {
                    println("ダウンロードに失敗しました")
                    return@forEach
                } finally {
                    outputStream?.close()
                }

                //mp4への変換
                println("${it.name}を変換開始")
                //画像を読み込み
                val bufferImage = ImageIO.read(imageFile)
                //mp4のファイル
                val videoFileName = it.name.replace("png", "mp4")
                val videoFile = File(cacheDirectory, videoFileName)
                //エンコーダー
                val encoder = SequenceEncoder.createSequenceEncoder(videoFile, 1)
                //フレームをエンコーダーに追加
                val picture = AWTUtil.fromBufferedImage(bufferImage, ColorSpace.RGB)
                //2フレーム入れる
                encoder.encodeNativeFrame(picture)
                encoder.encodeNativeFrame(picture)
                //エンコード
                encoder.finish()
                println("${videoFileName}を作成完了")

                //アップロード
                println("${videoFileName}をアップロード")
                //ストリーム作成
                FileInputStream(videoFile).let {
                    //送信
                    client.files().uploadBuilder("/${videoFileName}").withMode(WriteMode.OVERWRITE).uploadAndFinish(it)
                }

                println("${it.name}の変換終了")
            }
        }catch (e: BadRequestException) {
            println("DropBoxへのリクエストが失敗しました")
        }

        println("-- 終了しました --")

    }
}