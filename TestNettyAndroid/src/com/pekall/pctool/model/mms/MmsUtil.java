
package com.pekall.pctool.model.mms;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;

import android.content.ContentProviderOperation;
import android.content.ContentUris;
import android.content.Context;
import android.content.OperationApplicationException;
import android.database.Cursor;
import android.net.Uri;
import android.os.RemoteException;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.util.Log;

import com.pekall.pctool.model.contact.ContactUtil;
import com.pekall.pctool.model.mms.Mms.Attachment;
import com.pekall.pctool.model.mms.Mms.Slide;
import com.pekall.pctool.util.Slog;

public class MmsUtil {
    static final Uri MAIN_MMS_URI = Uri.parse("content://mms");
    private static final String MMS_AUTHORITY = "mms";

    public static List<Mms> query(Context cxt) {
        List<Mms> mmsList = new ArrayList<Mms>();
        Cursor cursor = cxt.getContentResolver().query(MAIN_MMS_URI, new String[] {
                "_id", "thread_id", "msg_box", "sub", "date", "read", "m_size"
        }, null, null, "_id desc");

        while (cursor.moveToNext()) {
            Mms mms = new Mms();

            mms.rowId = cursor.getLong(cursor.getColumnIndex("_id"));
            mms.threadId = cursor.getLong(cursor.getColumnIndex("thread_id"));
            mms.msgBoxIndex = cursor.getInt(cursor.getColumnIndex("msg_box"));
            mms.size = cursor.getInt(cursor.getColumnIndex("m_size"));

            Cursor cursorAddress = cxt.getContentResolver().query(Uri.parse("content://mms/" + mms.rowId + "/addr"),
                    null, null, null, null);

            if (cursorAddress.moveToFirst()) {
                mms.phoneNum = cursorAddress.getString(cursorAddress.getColumnIndex("address"));
            }
            cursorAddress.close();

            if (!TextUtils.isEmpty(mms.phoneNum)) {
                mms.person = ContactUtil.getRawContactId(cxt, mms.phoneNum);
            }

            String str = cursor.getString(cursor.getColumnIndex("sub"));
            if (str != null) {
                try {
                    str = new String(str.getBytes("ISO-8859-1"), "UTF-8");
                } catch (UnsupportedEncodingException e) {
                    Slog.e("encoding string " + e.getMessage());
                }
            }
            mms.subject = str;

            // the unit of date is second
            mms.date = cursor.getLong(cursor.getColumnIndex("date")) * DateUtils.SECOND_IN_MILLIS;
            mms.isReaded = cursor.getInt(cursor.getColumnIndex("read"));

            /** get smil.xml **/
            Cursor cursorSmil = cxt.getContentResolver().query(makeIdPartUri(mms.rowId), new String[] {
                    "text"
            }, "ct='application/smil'", null, null);
            String smilContent = "";
            if (cursorSmil.moveToFirst()) {
                smilContent = cursorSmil.getString(cursorSmil.getColumnIndex("text"));
                // Log.e("", "smil content = " + smilContent);
            } else {
                Slog.e("this mms don't have smil.xml");
            }
            cursorSmil.close();

            /** parse smil.xml and fill slide contents **/
            List<String> slideTxtFileNames = parseSmilToSlidePull(cxt, smilContent, mms);

            /** get attachments **/
            Cursor cursorPart = cxt.getContentResolver().query(makeIdPartUri(mms.rowId), null, null, null, null);
            while (cursorPart.moveToNext()) {
                String type = cursorPart.getString(cursorPart.getColumnIndex("ct"));
                long partRowId = cursorPart.getLong(cursorPart.getColumnIndex("_id"));
                String fileName = cursorPart.getString(cursorPart.getColumnIndex("cl"));
                /** avoid null pointer **/
                fileName = (fileName == null) ? "" : fileName;
                type = (type == null) ? "" : type;

                if (!type.equalsIgnoreCase("application/smil")
                        && (!fileName.endsWith(".txt") || !slideTxtFileNames.contains(fileName))
                        && !isSlideFile(mms.attachments, fileName))
                    loadAttachment(cxt, mms, partRowId, type, fileName);
            }
            cursorPart.close();

            mmsList.add(mms);
        } // << end while >>

        cursor.close();

        return mmsList;
    }

    private static void loadAttachment(Context cxt, Mms mms, long partRowId, String type, String fileName) {
        Attachment attachment = new Attachment();

        InputStream is = null;
        ByteArrayOutputStream baos = null;
        try {
            is = cxt.getContentResolver().openInputStream(makePartIdUri(partRowId));
            baos = new ByteArrayOutputStream();
            byte buf[] = new byte[1024];
            int numRead = 0;
            while ((numRead = is.read(buf)) != -1)
                baos.write(buf, 0, numRead);
            attachment.fileBytes = baos.toByteArray();
        } catch (IOException e) {
            Slog.e("read attachment file bytes " + e.getMessage());
        } finally {
            close(is);
            close(baos);
        }

        if (type.startsWith("image/")) {
            String imageSuffix = type.substring("image/".length());
            int i = fileName.lastIndexOf(".");
            /** if no suffix name, manually add one **/
            fileName = (i == -1) ? (fileName + "." + imageSuffix) : (fileName.substring(0, i) + "." + imageSuffix);
        }
        attachment.name = fileName;
        mms.attachments.add(attachment);
    }

    public static boolean delete(Context cxt, long rowId) {
        return cxt.getContentResolver().delete(ContentUris.withAppendedId(MAIN_MMS_URI, rowId), null, null) == 1;
    }

    public static boolean delete(Context ctx, List<Long> rowIds) {
        ArrayList<ContentProviderOperation> ops = new ArrayList<ContentProviderOperation>();

        for (long rowId : rowIds) {
            ops.add(ContentProviderOperation.newDelete(ContentUris.withAppendedId(MAIN_MMS_URI, rowId)).build());
        }
        try {
            ctx.getContentResolver().applyBatch(MMS_AUTHORITY, ops);
            return true;
        } catch (RemoteException e) {
            Slog.e("Error when deleteSms", e);
        } catch (OperationApplicationException e) {
            Slog.e("Error when deleteSms", e);
        }

        return false;
    }

    private static Uri makeIdPartUri(long id) {
        return Uri.parse("content://mms/" + id + "/part");
    }

    private static Uri makePartIdUri(long id) {
        return Uri.parse("content://mms/part/" + id);
    }

    private static void close(Closeable closeable) {
        if (closeable != null) {
            try {
                closeable.close();
            } catch (IOException e) {
                Slog.e("close error: " + e.getMessage());
            }
        }
    }

    private static List<String> parseSmilToSlidePull(Context cxt, String smilContent, Mms mms) {
        XmlPullParserFactory factory;
        List<String> slideTxtFileNames = new ArrayList<String>();
        try {
            factory = XmlPullParserFactory.newInstance();
            factory.setNamespaceAware(false);
            XmlPullParser xpp = factory.newPullParser();

            xpp.setInput(new StringReader(smilContent));
            int eventType = xpp.getEventType();
            String elementName = null;
            Slide slide = null;
            while (eventType != XmlPullParser.END_DOCUMENT) {
                if (eventType == XmlPullParser.START_DOCUMENT) {
//                    Slog.d("Start document");
                } else if (eventType == XmlPullParser.START_TAG) {
                    elementName = xpp.getName();
//                    Slog.d("Start tag " + elementName);
                    if ("par".equals(elementName)) {
                        int slideDuration = parseDuration(xpp.getAttributeValue(null, "dur"));
                        slide = new Slide(slideDuration);
                    } else if ("text".equals(elementName) && slide != null) {
                        String txtFileName = xpp.getAttributeValue(null, "src");
                        slideTxtFileNames.add(txtFileName);
                        Cursor cursorPart = cxt.getContentResolver().query(makeIdPartUri(mms.rowId), new String[] {
                                "text"
                        }, "cl='" + txtFileName + "'", null, null);
                        if (cursorPart.moveToFirst()) { 
                            slide.text = cursorPart.getString(cursorPart.getColumnIndex("text"));
                        }
                        cursorPart.close();
                    } else if ("img".equals(elementName)) {
                        String imageFileName = xpp.getAttributeValue(null, "src");
                        slide.imageIndex = gainAttachmentIndex(cxt, mms, imageFileName);
                    } else if ("audio".equals(elementName)) {
                        String audioFileName = xpp.getAttributeValue(null, "src");
                        slide.audioIndex = gainAttachmentIndex(cxt, mms, audioFileName);
                    } else if ("video".equals(elementName)) {
                        String videoFileName = xpp.getAttributeValue(null, "src");
                        slide.videoIndex = gainAttachmentIndex(cxt, mms, videoFileName);
                    }
                } else if (eventType == XmlPullParser.END_TAG) {
                    elementName = xpp.getName();
//                    Slog.d("End tag " + elementName);
                    if ("par".equals(elementName) && slide != null) {
                        mms.slides.add(slide);
                        slide = null;
                    }
                } else if (eventType == XmlPullParser.TEXT) {
//                    Slog.d("Text " + xpp.getText());
                }
                eventType = xpp.next();
            }
//            Slog.d("End document");
        } catch (XmlPullParserException e) {
            Slog.e("Error parse smil", e);
        } catch (IOException e) {
            Slog.e("Error parse smil", e);
        }
        return slideTxtFileNames;
    }

//    private static void parseSmilToSlide(final Context cxt, String smilContent, final Mms mms) {
//        RootElement root = new RootElement("smil");
//        Element par = root.getChild("body").getChild("par");
//
//        genericParseSmilToSlide(cxt, mms, par);
//        try {
//            Xml.parse(smilContent, root.getContentHandler());
//        } catch (Exception e) {
//            Slog.e("parse smil error: " + e.getMessage());
//            /** handle smil with namespace **/
//            parseSmilWithNamespaceToSlide(cxt, smilContent, mms);
//        }
//    }
//
//    private static void parseSmilWithNamespaceToSlide(final Context cxt, String smilContent, final Mms mms) {
//        RootElement root = new RootElement("http://www.w3.org/2001/SMIL20/Language", "smil");
//        Element par = root.getChild("http://www.w3.org/2001/SMIL20/Language", "body").getChild(
//                "http://www.w3.org/2001/SMIL20/Language", "par");
//
//        genericParseSmilToSlide(cxt, mms, par);
//        try {
//            Xml.parse(smilContent, root.getContentHandler());
//        } catch (Exception e) {
//            Slog.e("parse smil with namespace error: " + e.getMessage());
//        }
//    }
//
//    private static void genericParseSmilToSlide(final Context ctx, final Mms mms, Element par) {
//        slideTxtFileNames = new ArrayList<String>();
//
//        par.setStartElementListener(new StartElementListener() {
//            @Override
//            public void start(Attributes attrs) {
//                slideForParse = new Slide(parseDuration(attrs.getValue("dur")));
//            }
//        });
//        par.setEndElementListener(new EndElementListener() {
//            @Override
//            public void end() {
//                mms.slides.add(slideForParse);
//            }
//        });
//
//        par.getChild("text").setStartElementListener(new StartElementListener() {
//            @Override
//            public void start(Attributes attrs) {
//                String txtFileName = attrs.getValue("src");
//                slideTxtFileNames.add(txtFileName);
//                Cursor cursorPart = ctx.getContentResolver().query(makeIdPartUri(mms.rowId), new String[] {
//                        "text"
//                }, "cl='" + txtFileName + "'", null, null);
//                cursorPart.moveToFirst();
//                slideForParse.text = cursorPart.getString(cursorPart.getColumnIndex("text"));
//                cursorPart.close();
//            }
//        });
//
//        par.getChild("img").setStartElementListener(new StartElementListener() {
//            @Override
//            public void start(Attributes attrs) {
//                slideForParse.imageIndex = gainAttachmentIndex(ctx, mms, attrs.getValue("src"));
//            }
//        });
//
//        par.getChild("audio").setStartElementListener(new StartElementListener() {
//            @Override
//            public void start(Attributes attrs) {
//                slideForParse.audioIndex = gainAttachmentIndex(ctx, mms, attrs.getValue("src"));
//            }
//        });
//
//        par.getChild("video").setStartElementListener(new StartElementListener() {
//            @Override
//            public void start(Attributes attrs) {
//                slideForParse.videoIndex = gainAttachmentIndex(ctx, mms, attrs.getValue("src"));
//            }
//        });
//    }

    private static int parseDuration(String str) {
        if (!Character.isDigit(str.charAt(0))) {
            Log.e("", "duration 1st char is not digit");
            return 0;
        } else {
            int i = 1;
            while (Character.isDigit(str.charAt(i++)))
                ;
            if (str.substring(i - 1).equalsIgnoreCase("ms")) {
                return Integer.parseInt(str.substring(0, i - 1));
            } else if (str.substring(i - 1).equalsIgnoreCase("s")) {
                return Integer.parseInt(str.substring(0, i - 1)) * 1000;
            } else {
                Log.e("", "invalid duration measurement");
                return 0;
            }
        }
    }

    private static int gainAttachmentIndex(Context cxt, Mms mms, String fileName) {
        String type = null;
        long partRowId = 0;

        Cursor cursorPart = cxt.getContentResolver().query(makeIdPartUri(mms.rowId), null,
                "cl='" + fileName + "' OR name='" + fileName + "'", null, null);
        if (cursorPart.moveToFirst()) {
            type = cursorPart.getString(cursorPart.getColumnIndex("ct"));
            partRowId = cursorPart.getLong(cursorPart.getColumnIndex("_id"));
        }
        cursorPart.close();

        /** avoid null pointer **/
        type = (type == null) ? "" : type;
        loadAttachment(cxt, mms, partRowId, type, fileName);
        return mms.attachments.size() - 1;
    }

    private static boolean isSlideFile(ArrayList<Attachment> attachments, String fileName) {
        for (Attachment a : attachments) {
            int i = fileName.lastIndexOf(".");
            String fileNameWithoutSuffix = (i == -1) ? fileName : fileName.substring(0, i);
            i = a.name.lastIndexOf(".");
            String attachmentNameWithoutSuffix = (i == -1) ? a.name : a.name.substring(0, i);
            if (fileNameWithoutSuffix.equals(attachmentNameWithoutSuffix))
                return true;
        }
        return false;
    }

}
