package com.project_rating.png_to_mp4

import com.github.syari.kgit.KGit
import org.bytedeco.ffmpeg.global.avcodec.AV_CODEC_ID_H264
import org.bytedeco.javacv.FFmpegFrameGrabber
import org.bytedeco.javacv.FFmpegFrameRecorder
import org.bytedeco.javacv.Java2DFrameConverter
import org.eclipse.jgit.errors.RepositoryNotFoundException
import org.eclipse.jgit.transport.URIish
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider
import java.awt.AlphaComposite
import java.awt.image.BufferedImage
import java.io.File
import java.nio.file.Paths
import java.util.*

fun main(args: Array<String>) {
    //引数があるかを確認
    if (args.isEmpty()) {
        println("第１引数にGitHubのTokenを入力してください")
        return
    }
    if (args.size < 2) {
        println("第２引数にGitHubのレポジトリのURLを入力してください")
        return
    }

    //1枚あたりのレート
    var rate = 20
    //フェード時間
    var fade = 0
    //FPS
    var fps = -1

    //引数を確認
    args.forEach {
        //レート
        if (it.startsWith("rate=")) {
            rate = it.replace("rate=", "").toInt()
            println("1枚当たりの時間を${rate}秒にしました")
        }
        //フェード
        if (it.startsWith("fade=")) {
            fade = it.replace("fade=", "").toInt()
            println("フェード時間を${fade}秒にしました")
        }
        //fps
        if (it.startsWith("fps=")) {
            fps = it.replace("fps=", "").toInt()
            println("動画のフレームレートを${fps}にしました")
        }
    }

    //fpsが初期値化を確認
    if (fps == -1) {
        //fpsが必要じゃない時
        fps = if (fade == 0) {
            //1にする
            1
        } else {
            //30にする
            30
        }
    }

    //開始
    PNG2MP4(args[0], args[1], rate, fade, fps)
}

/**
 * PNGをMP4に変換する
 *
 *
 * @param token DropBoxのToken
 * @param rate スライドショー時の1枚当たりの時間
 * @param fade フェードにかける時間  0だとフェードなし
 * @param fps スライドショーのフレームレート
 */
class PNG2MP4(token: String, url: String, rate: Int, private val fade: Int, private val fps: Int) {
    init {

        //jarがあるディレクトリ
        val parentDirectory: File =
            File(Paths.get(javaClass.protectionDomain.codeSource.location.toURI()).toString()).parentFile


        //キャッシュのディレクトリ
        val cacheDirectory = File(parentDirectory, "cache")
        //ディレクトリがあるかを確認
        if (!cacheDirectory.exists()) {
            //ディレクトリ作成
            cacheDirectory.mkdirs()
        }

        //Gitのレポジトリ
        val git: KGit = try {
            //開く
            KGit.open(cacheDirectory)
        } catch (e: RepositoryNotFoundException) {
            //初期化の処理
            println("初期化中")
            KGit.init {
                //ディレクトリを設定
                setDirectory(cacheDirectory)
                //Gitのディレクトリを設定
                setGitDir(File(cacheDirectory, ".git"))
            }
            //クローン
            println("クローン中...")
            KGit.cloneRepository {
                //ディレクトリを設定
                setDirectory(cacheDirectory)
                //Gitのディレクトリを設定
                setGitDir(File(cacheDirectory, ".git"))
                //リモートディレクトリを設定
                setURI(url)
                //タイムアウト時間
                setTimeout(60)
                //Tokenによるログイン
                setCredentialsProvider(UsernamePasswordCredentialsProvider(token, ""))
            }
        }

        //プル
        git.pull {
            //Tokenによるログイン
            setCredentialsProvider(UsernamePasswordCredentialsProvider(token, ""))
        }

        //リモートを設定
        git.remoteAdd {
            //origin
            setName("origin")
            //リモートディレクトリ
            setUri(URIish(url))
        }

        //変換開始
        println("変換を開始")

        //ファイル名をのリスト
        val files = cacheDirectory.listFiles().map { it.name }.filter { it.endsWith("png") }.toMutableList()

        //動画が複数枚で構成されるファイルを保管
        val multiImageFiles = hashMapOf<String, SortedSet<String>>()

        //_1.pngなどで終わるパターン
        val multiImagePattern = Regex("_\\d+.png")

        //ファイルを全て確認
        files.forEach {
            //パターンと照合
            if (it.contains(multiImagePattern)) {
                //ソートされるSetに追加
                multiImageFiles.getOrPut(it.replace(multiImagePattern, "_1.png")) { sortedSetOf() }.add(it)
            }
        }

        //複数枚で構成されるファイルを削除
        files.removeAll(multiImageFiles.values.flatten().toSet())

        //単フレームの画像の処理
        println("単フレームのファイルの処理を開始")
        files.forEach {
            //pngのFile
            val imageFile = File(cacheDirectory, it)


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

            //Gitに追加
            git.add {
                //ファイルを指定
                addFilepattern(videoFileName)
            }

            println("${it}の変換終了")
        }


        //複数枚の画像の処理
        println("スライドショー式のファイルを処理開始")
        multiImageFiles.forEach keyForEach@{
            //mp4のファイル
            val videoFileName = it.key.replace(multiImagePattern, ".mp4")
            val videoFile = File(cacheDirectory, videoFileName)

            //レコーダー
            var recorder: FFmpegFrameRecorder? = null

            //フェード用
            var startImage: BufferedImage? = null
            var beforeImage: BufferedImage? = null

            it.value.withIndex().forEach { indexedValue ->
                val index = indexedValue.index
                val fileName = indexedValue.value

                //pngのFile
                val imageFile = File(cacheDirectory, fileName)

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
                        // フェードがある時は30fpsにする
                        frameRate = fps.toDouble()
                        videoQuality = 1.0
                        format = "mp4"
                        //レコーダーを開始
                        start()
                    }
                }

                //フレームのデータ
                val frame = imageFileGrabber.grab()

                //フェードを行う
                if (fade != 0) {
                    //最初の画像の場合
                    if (index == 0) {
                        //最初のフレーム
                        startImage = Java2DFrameConverter().convert(frame)
                    } else {
                        //フレームをBufferImageに
                        val frameBufferImage = Java2DFrameConverter().convert(frame)

                        //フェードを入れる
                        recordFade(recorder!!, beforeImage!!, frameBufferImage)
                    }
                    //1個前のフレームとして記録
                    beforeImage = Java2DFrameConverter().convert(frame)
                }

                //秒数分フレームを入れる
                for (i in 0 until rate * fps) {
                    recorder!!.record(frame)
                }

                //画像の処理を終了
                imageFileGrabber.close()
            }

            //最初に戻るフェードが必要かを確認
            if (startImage != null && beforeImage != null && startImage != beforeImage) {
                //フェードを入れる
                recordFade(recorder!!, beforeImage!!, startImage!!)
            }

            //レコーダーを終了
            recorder?.close()
            println("${videoFileName}を作成完了")

            //Gitに追加
            git.add {
                //ファイルを指定
                addFilepattern(videoFileName)
            }

            println("${it.key.replace(multiImagePattern, ".png")}=${it.value}の変換終了")
        }

        //コミット
        git.commit {
            message = "PNG2MP4 update ()"
        }

        //プッシュ
        git.push {
            //Tokenによるログイン
            setCredentialsProvider(UsernamePasswordCredentialsProvider(token, ""))
        }

        println("-- 終了しました --")
    }


    /**
     * 2枚の画像からフェードをレコードする
     *
     * @param recorder レコーダー
     * @param baseImage 元画像
     * @param overlapImage 重ねる画像
     */
    private fun recordFade(recorder: FFmpegFrameRecorder, baseImage: BufferedImage, overlapImage: BufferedImage) {
        //fps*秒数 回繰り返す
        for (i in 0 until (fps * fade)) {
            val image = BufferedImage(baseImage.width, baseImage.height, baseImage.type).apply {
                data = baseImage.data
            }
            //Graphics2Dを作成
            val graphics = image.createGraphics()
            //透明度を設定
            graphics.composite = AlphaComposite.getInstance(
                AlphaComposite.SRC_OVER,
                (1.0 / (fps * fade) * i).toFloat()
            )
            //合成
            graphics.drawImage(overlapImage, 0, 0, null)
            //フレームを入れる
            recorder.record(Java2DFrameConverter().convert(image))
            //破棄
            graphics.dispose()
        }
    }
}