package com.project_rating.png_to_mp4

import com.dropbox.core.BadRequestException
import com.dropbox.core.DbxRequestConfig
import com.dropbox.core.InvalidAccessTokenException
import com.dropbox.core.v2.DbxClientV2
import com.dropbox.core.v2.auth.AuthError
import com.dropbox.core.v2.files.WriteMode
import org.bytedeco.ffmpeg.global.avcodec.AV_CODEC_ID_H264
import org.bytedeco.javacv.FFmpegFrameGrabber
import org.bytedeco.javacv.FFmpegFrameRecorder
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.file.Paths
import java.util.*

fun main(args: Array<String>) {
    //引数があるかを確認
    if (args.isEmpty()){
        println("第１引数にDropBoxのTokenを入力してください")
        return
    }

    //レートを取得
    val rate = if (args.size < 2){
        //20秒
        20
    }else {
        //引数から持ってくる
        args[1].toInt()
    }

    //開始
    PNG2MP4(args[0], rate)
}

class PNG2MP4(token: String, rate: Int) {
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
            //ファイル名をのリスト
            val files = arrayListOf<String>()

            client.files().listFolder("").entries.forEach {
                //pngかを確認
                if (!it.name.endsWith("png")) return@forEach

                //ファイル一覧に追加
                files.add(it.name)
            }


            //動画が複数枚で構成されるファイルを保管
            val multiImageFiles = hashMapOf<String, SortedSet<String>>()

            //_1.pngなどで終わるパターン
            val multiImagePattern = Regex("_\\d+.png")

            //ファイルを全て確認
            files.forEach{
                //パターンと照合
                if (it.contains(multiImagePattern)){
                    //ソートされるSetに追加
                    multiImageFiles.getOrPut(it.replace(multiImagePattern, "_1.png")) { sortedSetOf() }.add(it)
                }
            }

            //複数枚で構成されるファイルを削除
            files.removeAll(multiImageFiles.values.flatten().toSet())

            //単フレームの画像の処理
            println("単フレームのファイルの処理を開始")
            files.forEach{
                //pngのFile
                val imageFile = File(cacheDirectory, it)

                //ダウンロード
                println("${it}をダウンロード")
                var outputStream: FileOutputStream? = null
                try {
                    //ストリームを作成
                    outputStream = FileOutputStream(imageFile).apply {
                        //ダウンロード
                        client.files().download("/${it}").download(this)
                    }
                } catch (ignore: Exception) {
                    println("ダウンロードに失敗しました")
                    return@forEach
                } finally {
                    outputStream?.close()
                }

                //mp4への変換
                println("${it}を変換開始")
                //画像を読み込み
                val imageFileGrabber = FFmpegFrameGrabber(imageFile).apply {
                    //開始
                    start()
                }

                //mp4のファイル
                val videoFileName = it.replace(".png", ".mp4")
                val videoFile = File(cacheDirectory, videoFileName)

                //レコーダーを作成
                val recorder =
                    FFmpegFrameRecorder(videoFile, imageFileGrabber.imageWidth, imageFileGrabber.imageHeight).apply {
                        //各種値を設定
                        videoCodec = AV_CODEC_ID_H264
                        sampleRate = 1
                        frameRate = 1.0
                        videoQuality = 1.0
                        format = "mp4"
                        timestamp = 2
                        //レコーダーを開始
                        start()
                    }

                //フレームのデータ
                val frame = imageFileGrabber.grab()
                //2フレーム言える
                recorder.record(frame)
                recorder.record(frame)

                //画像の処理を終了
                imageFileGrabber.close()
                //レコーダーを終了
                recorder.close()
                println("${videoFileName}を作成完了")

                //アップロード
                println("${videoFileName}をアップロード")
                //ストリーム作成
                FileInputStream(videoFile).let {
                    //送信
                    client.files().uploadBuilder("/${videoFileName}").withMode(WriteMode.OVERWRITE).uploadAndFinish(it)
                }

                println("${it}の変換終了")
            }


            //複数枚の画像の処理
            println("スライドショー式のファイルを処理開始")
            multiImageFiles.forEach keyForEach@{ it ->
                //mp4のファイル
                val videoFileName = it.key.replace(multiImagePattern, ".mp4")
                val videoFile = File(cacheDirectory, videoFileName)

                //レコーダー
                var recorder: FFmpegFrameRecorder? = null

                it.value.forEach { fileName ->
                    //pngのFile
                    val imageFile = File(cacheDirectory, fileName)

                    //ダウンロード
                    println("${fileName}をダウンロード")
                    var outputStream: FileOutputStream? = null
                    try {
                        //ストリームを作成
                        outputStream = FileOutputStream(imageFile).apply {
                            //ダウンロード
                            client.files().download("/${fileName}").download(this)
                        }
                    } catch (ignore: Exception) {
                        println("ダウンロードに失敗しました")
                        return@keyForEach
                    } finally {
                        outputStream?.close()
                    }

                    //mp4への変換
                    println("${fileName}を変換開始")
                    //画像を読み込み
                    val imageFileGrabber = FFmpegFrameGrabber(imageFile).apply {
                        //開始
                        start()
                    }

                    //レコーダーが存在するかを確認
                    if (recorder == null) {
                        //レコーダーを作成
                        recorder = FFmpegFrameRecorder(
                            videoFile,
                            imageFileGrabber.imageWidth,
                            imageFileGrabber.imageHeight
                        ).apply {
                            //各種値を設定
                            videoCodec = AV_CODEC_ID_H264
                            frameRate = 1.0
                            videoQuality = 1.0
                            format = "mp4"
                            //レコーダーを開始
                            start()
                        }
                    }

                    //フレームのデータ
                    val frame = imageFileGrabber.grab()
                    //秒数分フレームを入れる
                    for (i in 0 until rate) {
                        recorder!!.record(frame)
                    }

                    //画像の処理を終了
                    imageFileGrabber.close()
                }

                //レコーダーを終了
                recorder?.close()
                println("${videoFileName}を作成完了")

                //アップロード
                println("${videoFileName}をアップロード")
                //ストリーム作成
                FileInputStream(videoFile).let {
                    //送信
                    client.files().uploadBuilder("/${videoFileName}").withMode(WriteMode.OVERWRITE).uploadAndFinish(it)
                }

                println("${it.key.replace(multiImagePattern, ".png")}=${it.value}の変換終了")
            }
        } catch (e: BadRequestException) {
            println("DropBoxへのリクエストが失敗しました")
            e.printStackTrace()
        } catch (e: InvalidAccessTokenException) {
            if (e.authError == AuthError.EXPIRED_ACCESS_TOKEN) {
                println("DropBoxのトークンが期限切れです\nDropBoxのアプリコンソールより、トークンを取得し直してください")
            } else {
                println("DropBoxのトークンが無効です\nDropBoxのアプリコンソールより、トークンを取得し直してください")
            }
            e.printStackTrace()
        }

        println("-- 終了しました --")
    }
}