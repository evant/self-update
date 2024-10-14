/*
 * Copyright (C) 2006 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package me.tatarka.android.selfupdate

import android.os.Build
import android.os.PersistableBundle
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import org.xmlpull.v1.XmlSerializer

internal object XmlUtils {
    @Suppress("DEPRECATION")
    fun writeBundleXml(bundle: PersistableBundle, out: XmlSerializer) {
        val keys = bundle.keySet()
        for (key in keys) {
            val value = bundle.get(key)
            writeValueXml(value, key, out)
        }
    }

    fun writeBundleXml(bundle: PersistableBundle, name: String?, out: XmlSerializer) {
        out.startTag(null, "pbundle_as_map")
        if (name != null) {
            out.attribute(null, "name", name)
        }
        writeBundleXml(bundle, out)
        out.endTag(null, "pbundle_as_map")
    }

    fun writeIntArrayXml(value: IntArray, name: String?, out: XmlSerializer) {
        out.startTag(null, "int-array")
        if (name != null) {
            out.attribute(null, "name", name)
        }

        val size = value.size
        out.attribute(null, "num", size.toString())

        for (i in 0 until size) {
            out.startTag(null, "item")
            out.attribute(null, "value", value[i].toString())
            out.endTag(null, "item")
        }

        out.endTag(null, "int-array")
    }

    fun writeLongArrayXml(value: LongArray, name: String?, out: XmlSerializer) {
        out.startTag(null, "long-array")
        if (name != null) {
            out.attribute(null, "name", name)
        }

        val size = value.size
        out.attribute(null, "num", size.toString())

        for (i in 0 until size) {
            out.startTag(null, "item")
            out.attribute(null, "value", value[i].toString())
            out.endTag(null, "item")
        }

        out.endTag(null, "long-array")
    }

    fun writeDoubleArrayXml(value: DoubleArray, name: String?, out: XmlSerializer) {
        out.startTag(null, "double-array")
        if (name != null) {
            out.attribute(null, "name", name)
        }

        val size = value.size
        out.attribute(null, "num", size.toString())

        for (i in 0 until size) {
            out.startTag(null, "item")
            out.attribute(null, "value", value[i].toString())
            out.endTag(null, "item")
        }

        out.endTag(null, "double-array")
    }

    fun writeStringArrayXml(value: Array<String>, name: String?, out: XmlSerializer) {
        out.startTag(null, "string-array")
        if (name != null) {
            out.attribute(null, "name", name)
        }

        val size = value.size
        out.attribute(null, "num", size.toString())

        for (i in 0 until size) {
            out.startTag(null, "item")
            out.attribute(null, "value", value[i])
            out.endTag(null, "item")
        }

        out.endTag(null, "string-array")
    }

    fun writeBooleanArrayXml(value: BooleanArray, name: String?, out: XmlSerializer) {
        out.startTag(null, "boolean-array")
        out.attribute(null, "name", name)

        val size = value.size
        if (name != null) {
            out.attribute(null, "num", size.toString())
        }

        for (i in 0 until size) {
            out.startTag(null, "item")
            out.attribute(null, "value", value[i].toString())
            out.endTag(null, "item")
        }

        out.endTag(null, "boolean-array")
    }

    fun writeValueXml(value: Any?, name: String?, out: XmlSerializer) {
        val typeStr: String
        when (value) {
            null -> {
                out.startTag(null, "null")
                out.attribute(null, "name", name)
                out.endTag(null, "null")
                return
            }

            is String -> {
                out.startTag(null, "string");
                out.attribute(null, "name", name);
                out.text(value);
                out.endTag(null, "string");
                return
            }

            is Int -> {
                typeStr = "int"
            }

            is Long -> {
                typeStr = "long";
            }

            is Float -> {
                typeStr = "float";
            }

            is Double -> {
                typeStr = "double";
            }

            is Boolean -> {
                typeStr = "boolean";
            }

            is IntArray -> {
                writeIntArrayXml(value, name, out)
                return
            }

            is LongArray -> {
                writeLongArrayXml(value, name, out)
                return
            }

            is DoubleArray -> {
                writeDoubleArrayXml(value, name, out)
                return
            }

            is BooleanArray -> {
                writeBooleanArrayXml(value, name, out)
                return
            }

            is Array<*> -> {
                // assume string array
                writeStringArrayXml(value as Array<String>, name, out)
                return
            }

            is PersistableBundle -> {
                writeBundleXml(value, name, out)
                return
            }

            is CharSequence -> {
                // XXX This is to allow us to at least write something if
                // we encounter styled text...  but it means we will drop all
                // of the styling information. :(
                out.startTag(null, "string")
                out.attribute(null, "name", name)
                out.text(value.toString())
                out.endTag(null, "string")
                return
            }

            else -> {
                throw RuntimeException("writeValueXml: unable to write value $value")
            }
        }

        out.startTag(null, typeStr)
        out.attribute(null, "name", name)
        out.attribute(null, "value", value.toString())
        out.endTag(null, typeStr)
    }

    fun readThisBundleXml(parser: XmlPullParser, endTag: String): PersistableBundle {
        val bundle = PersistableBundle()
        var eventType = parser.eventType
        do {
            if (eventType == XmlPullParser.START_TAG) {
                readThisValueIntoBundleXml(parser, bundle)
            } else if (eventType == XmlPullParser.END_TAG) {
                if (parser.name == endTag) {
                    return bundle
                }
                throw XmlPullParserException("Expected $endTag end tag at: ${parser.name}")
            }
            eventType = parser.next()
        } while (eventType != XmlPullParser.END_DOCUMENT)

        throw XmlPullParserException("Document ended before $endTag end tag");
    }

    fun readThisIntArrayIntoBundleXml(
        parser: XmlPullParser,
        endTag: String,
        name: String?,
        bundle: PersistableBundle,
    ) {
        val num = parser.getAttributeValue(null, "num")?.toIntOrNull() ?: 0
        parser.next()

        val array = IntArray(num)
        var i = 0

        var eventType = parser.eventType
        do {
            if (eventType == XmlPullParser.START_TAG) {
                if (parser.name == "item") {
                    array[i] = parser.getAttributeValue(null, "value").toIntOrNull() ?: 0
                } else {
                    throw XmlPullParserException("Expected item tag at: ${parser.name}")
                }
            } else if (eventType == XmlPullParser.END_TAG) {
                if (parser.name.equals(endTag)) {
                    bundle.putIntArray(name, array)
                    return
                } else if (parser.name == "item") {
                    i++
                } else {
                    throw XmlPullParserException("Expected $endTag end tag at: ${parser.name}")
                }
            }
            eventType = parser.next()
        } while (eventType != XmlPullParser.END_DOCUMENT)

        throw XmlPullParserException("Document ended before $endTag end tag")
    }

    fun readThisLongArrayIntoBundleXml(
        parser: XmlPullParser,
        endTag: String,
        name: String?,
        bundle: PersistableBundle,
    ) {
        val num = parser.getAttributeValue(null, "num")?.toIntOrNull() ?: 0
        parser.next()

        val array = LongArray(num)
        var i = 0

        var eventType = parser.eventType
        do {
            if (eventType == XmlPullParser.START_TAG) {
                if (parser.name.equals("item")) {
                    array[i] = parser.getAttributeValue(null, "value").toLongOrNull() ?: 0
                } else {
                    throw XmlPullParserException("Expected item tag at: ${parser.name}")
                }
            } else if (eventType == XmlPullParser.END_TAG) {
                if (parser.name == endTag) {
                    bundle.putLongArray(name, array)
                    return
                } else if (parser.name == "item") {
                    i++
                } else {
                    throw XmlPullParserException("Expected $endTag end tag at: ${parser.name}")
                }
            }
            eventType = parser.next()
        } while (eventType != XmlPullParser.END_DOCUMENT)

        throw XmlPullParserException("Document ended before $endTag end tag")
    }

    fun readThisDoubleArrayIntoBundleXml(
        parser: XmlPullParser,
        endTag: String,
        name: String?,
        bundle: PersistableBundle,
    ) {
        val num = parser.getAttributeValue(null, "num")?.toIntOrNull() ?: 0
        parser.next()

        val array = DoubleArray(num)
        var i = 0

        var eventType = parser.eventType
        do {
            if (eventType == XmlPullParser.START_TAG) {
                if (parser.name.equals("item")) {
                    array[i] = parser.getAttributeValue(null, "value").toDoubleOrNull() ?: 0.0
                } else {
                    throw XmlPullParserException("Expected item tag at: ${parser.name}")
                }
            } else if (eventType == XmlPullParser.END_TAG) {
                if (parser.name == endTag) {
                    bundle.putDoubleArray(name, array)
                    return
                } else if (parser.name == "item") {
                    i++
                } else {
                    throw XmlPullParserException("Expected $endTag end tag at: ${parser.name}")
                }
            }
            eventType = parser.next()
        } while (eventType != XmlPullParser.END_DOCUMENT)

        throw XmlPullParserException("Document ended before $endTag end tag")
    }

    fun readThisStringArrayIntoBundleXml(
        parser: XmlPullParser,
        endTag: String,
        name: String?,
        bundle: PersistableBundle,
    ) {
        val num = parser.getAttributeValue(null, "num")?.toIntOrNull() ?: 0
        parser.next()

        val array = Array<String?>(num) { null }
        var i = 0

        var eventType = parser.eventType
        do {
            if (eventType == XmlPullParser.START_TAG) {
                if (parser.name.equals("item")) {
                    array[i] = parser.getAttributeValue(null, "value")
                } else {
                    throw XmlPullParserException("Expected item tag at: ${parser.name}")
                }
            } else if (eventType == XmlPullParser.END_TAG) {
                if (parser.name == endTag) {
                    bundle.putStringArray(name, array)
                    return
                } else if (parser.name == "item") {
                    i++
                } else {
                    throw XmlPullParserException("Expected $endTag end tag at: ${parser.name}")
                }
            }
            eventType = parser.next()
        } while (eventType != XmlPullParser.END_DOCUMENT)

        throw XmlPullParserException("Document ended before $endTag end tag")
    }

    fun readThisBooleanArrayIntoBundleXml(
        parser: XmlPullParser,
        endTag: String,
        name: String?,
        bundle: PersistableBundle,
    ) {
        val num = parser.getAttributeValue(null, "num")?.toIntOrNull() ?: 0
        parser.next()

        val array = BooleanArray(num)
        var i = 0

        var eventType = parser.eventType
        do {
            if (eventType == XmlPullParser.START_TAG) {
                if (parser.name.equals("item")) {
                    array[i] =
                        parser.getAttributeValue(null, "value").toBooleanStrictOrNull() ?: false
                } else {
                    throw XmlPullParserException("Expected item tag at: ${parser.name}")
                }
            } else if (eventType == XmlPullParser.END_TAG) {
                if (parser.name == endTag) {
                    if (Build.VERSION.SDK_INT >= 22) {
                        bundle.putBooleanArray(name, array)
                    }
                    return
                } else if (parser.name == "item") {
                    i++
                } else {
                    throw XmlPullParserException("Expected $endTag end tag at: ${parser.name}")
                }
            }
            eventType = parser.next()
        } while (eventType != XmlPullParser.END_DOCUMENT)

        throw XmlPullParserException("Document ended before $endTag end tag")
    }

    fun readThisValueIntoBundleXml(
        parser: XmlPullParser,
        bundle: PersistableBundle,
    ) {
        val valueName = parser.getAttributeValue(null, "name")
        val tagName = parser.name

        when {
            tagName == "null" -> {
                bundle.putString(valueName, null)
            }

            tagName == "string" -> {
                val value = StringBuilder()
                var eventType: Int
                while ((parser.next().also { eventType = it }) != XmlPullParser.END_DOCUMENT) {
                    if (eventType == XmlPullParser.END_TAG) {
                        if (parser.name == "string") {
                            bundle.putString(valueName, value.toString())
                            return
                        }
                        throw XmlPullParserException("Unexpected end tag in <string>: ${parser.name}")
                    } else if (eventType == XmlPullParser.TEXT) {
                        value.append(parser.text)
                    } else if (eventType == XmlPullParser.START_TAG) {
                        throw XmlPullParserException("Unexpected start tag in <string>: ${parser.name}")
                    }
                }
                throw XmlPullParserException("Unexpected end of document in <string>")
            }

            readThisPrimitiveValueIntoBundleXml(parser, tagName, valueName, bundle) -> {
                // all work already done by readThisPrimitiveValueXml
            }

            tagName == "int-array" -> {
                readThisIntArrayIntoBundleXml(parser, "int-array", valueName, bundle);
                return
            }

            tagName == "long-array" -> {
                readThisLongArrayIntoBundleXml(parser, "long-array", valueName, bundle);
                return
            }

            tagName == "double-array" -> {
                readThisDoubleArrayIntoBundleXml(parser, "double-array", valueName, bundle);
                return
            }

            tagName == "string-array" -> {
                readThisStringArrayIntoBundleXml(parser, "string-array", valueName, bundle);
                return
            }

            tagName == "boolean-array" -> {
                readThisBooleanArrayIntoBundleXml(parser, "boolean-array", valueName, bundle);
                return
            }

            tagName == "pbundle_as_map" -> {
                parser.next()
                bundle.putPersistableBundle(valueName, readThisBundleXml(parser, "pbundle_as_map"))
                return
            }

            else -> {
                throw XmlPullParserException("Unknown tag: $tagName")
            }
        }
    }

    fun readThisPrimitiveValueIntoBundleXml(
        parser: XmlPullParser,
        tagName: String,
        name: String?,
        bundle: PersistableBundle,
    ): Boolean {
        return when (tagName) {
            "int" -> {
                bundle.putInt(name, parser.getAttributeValue(null, "value").toInt())
                true
            }

            "long" -> {
                bundle.putLong(name, parser.getAttributeValue(null, "value").toLong())
                true
            }

            "double" -> {
                bundle.putDouble(name, parser.getAttributeValue(null, "value").toDouble())
                true
            }

            "boolean" -> {
                if (Build.VERSION.SDK_INT >= 22) {
                    bundle.putBoolean(name, parser.getAttributeValue(null, "value").toBoolean())
                    true
                } else {
                    false
                }
            }

            else -> false
        }
    }
}