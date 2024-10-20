package me.tatarka.android.selfupdate.compat

import android.os.Build
import android.os.PersistableBundle
import android.util.Xml
import androidx.annotation.RequiresApi
import me.tatarka.android.selfupdate.XmlUtils
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import org.xmlpull.v1.XmlSerializer
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.charset.StandardCharsets

internal object PersistableBundleCompat {
    private interface Impl {
        fun readFromStream(inputStream: InputStream): PersistableBundle

        fun writeToStream(bundle: PersistableBundle, outputStream: OutputStream)
    }

    @RequiresApi(30)
    private class Api30 : Impl {
        override fun readFromStream(inputStream: InputStream): PersistableBundle {
            return PersistableBundle.readFromStream(inputStream)
        }

        override fun writeToStream(bundle: PersistableBundle, outputStream: OutputStream) {
            bundle.writeToStream(outputStream)
        }
    }

    private class Api : Impl {
        override fun readFromStream(inputStream: InputStream): PersistableBundle {
            val parser = Xml.newPullParser()
            parser.setInput(inputStream, StandardCharsets.UTF_8.name())
            parser.next()
            return restoreFromXml(parser)
        }

        override fun writeToStream(bundle: PersistableBundle, outputStream: OutputStream) {
            val serializer = Xml.newSerializer()
            serializer.setOutput(outputStream, StandardCharsets.UTF_8.name())
            serializer.startTag(null, "bundle")
            try {
                saveToXml(bundle, serializer)
            } catch (e: XmlPullParserException) {
                throw IOException(e)
            }
            serializer.endTag(null, "bundle")
            serializer.flush()
        }

        private fun saveToXml(bundle: PersistableBundle, serializer: XmlSerializer) {
            XmlUtils.writeBundleXml(bundle, serializer)
        }

        private fun restoreFromXml(parser: XmlPullParser): PersistableBundle {
            val outerDepth = parser.depth
            val startTag = parser.name
            var event: Int
            while (((parser.next().also { event = it }) != XmlPullParser.END_DOCUMENT) &&
                (event != XmlPullParser.END_TAG || parser.depth < outerDepth)
            ) {
                if (event == XmlPullParser.START_TAG) {
                    // Don't throw an exception when restoring from XML since an attacker could try to
                    // input invalid data in the persisted file.
                    try {
                        return XmlUtils.readThisBundleXml(parser, startTag)
                    } catch (e: XmlPullParserException) {
                        return PersistableBundle()
                    }
                }
            }
            return PersistableBundle()
        }
    }

    private val IMPL = if (Build.VERSION.SDK_INT >= 30) {
        Api30()
    } else {
        Api()
    }

    @JvmStatic
    @Throws(IOException::class)
    fun readFromStream(stream: InputStream): PersistableBundle {
        return IMPL.readFromStream(stream)
    }

    @JvmStatic
    @Throws(IOException::class)
    fun writeToStream(bundle: PersistableBundle, stream: OutputStream) {
        IMPL.writeToStream(bundle, stream)
    }
}