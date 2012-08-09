
package com.pekall.pctool.client;

import com.example.tutorial.AddressBookProtos.AddressBook;
import com.example.tutorial.AddressBookProtos.Person;
import com.pekall.pctool.protos.AppInfoProtos.AppInfoP;
import com.pekall.pctool.protos.AppInfoProtos.AppInfoPList;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.util.EntityUtils;

import java.io.IOException;

public class Main {

    public static void main(String[] args) {
        try {
//            System.out.println("adb install -r TestNettyAndroid.apk");
//            executeCommand("/home/dev01/opt/android-sdk/platform-tools/adb install -r TestProtobufAndroidServer.apk");
//            Thread.sleep(3000);
            System.out.println("adb shell am broadcast -a com.pekall.pctool.AmCommandReceiver.ACTION_MAIN_SERVER_STOP -n com.pekall.pctool/.AmCommandReceiver");
            executeCommand("/home/dev01/opt/android-sdk/platform-tools/adb shell am broadcast -a com.pekall.pctool.AmCommandReceiver.ACTION_MAIN_SERVER_STOP -n com.pekall.pctool/.AmCommandReceiver");
            Thread.sleep(3000);

            System.out.println("adb shell am broadcast -a com.pekall.pctool.AmCommandReceiver.ACTION_MAIN_SERVER_START -n com.pekall.pctool/.AmCommandReceiver");
            executeCommand("/home/dev01/opt/android-sdk/platform-tools/adb shell am broadcast -a com.pekall.pctool.AmCommandReceiver.ACTION_MAIN_SERVER_START -n com.pekall.pctool/.AmCommandReceiver");
            Thread.sleep(3000);

            System.out.println("adb forward tcp:12580 tcp:12580");
            executeCommand("/home/dev01/opt/android-sdk/platform-tools/adb forward tcp:12580 tcp:12580");
            Thread.sleep(3000);
        } catch (InterruptedException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

        testGetAddressBook();
        
        testGetAppInfoPList();
        
        try {
            Thread.sleep(3000);
        } catch (InterruptedException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        System.out.println("adb shell am broadcast -a com.pekall.pctool.AmCommandReceiver.ACTION_MAIN_SERVER_STOP -n com.pekall.pctool/.AmCommandReceiver");
        executeCommand("/home/dev01/opt/android-sdk/platform-tools/adb shell am broadcast -a com.pekall.pctool.AmCommandReceiver.ACTION_MAIN_SERVER_STOP -n com.pekall.pctool/.AmCommandReceiver");

    }
    
    private static void executeCommand(String cmd) {
        try {
            Process proc = Runtime.getRuntime().exec(cmd);
            
            StreamGobbler errorGobbler = new StreamGobbler(proc.getErrorStream(), "ERROR");          
            // any output?   
            StreamGobbler outputGobbler = new StreamGobbler(proc.getInputStream(), "OUTPUT");   
                   
            // kick them off   
            errorGobbler.start();   
            outputGobbler.start();   
                                       
            // any error???   
            int exitVal = proc.waitFor();   
            System.out.println("ExitValue: " + exitVal);   
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (InterruptedException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    private static void testGetAddressBook() {
        HttpClient client = new DefaultHttpClient();
        HttpGet get = new HttpGet("http://localhost:12580/test");
        try {
            HttpResponse response = client.execute(get);
            HttpEntity entity = response.getEntity();
            AddressBook addressBook = AddressBook.parseFrom(entity.getContent());
            EntityUtils.consume(entity);

            for (Person p : addressBook.getPersonList()) {
                System.out.println("{id: " + p.getId() + ", name: " + p.getName() + "}");
            }

        } catch (ClientProtocolException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } finally {
            get.releaseConnection();
            client.getConnectionManager().shutdown();
        }
    }
    
    private static void testGetAppInfoPList() {
        HttpClient client = new DefaultHttpClient();
        HttpGet get = new HttpGet("http://localhost:12580/app");
        try {
            HttpResponse response = client.execute(get);
            HttpEntity entity = response.getEntity();
            AppInfoPList appInfoPList = AppInfoPList.parseFrom(entity.getContent());
            EntityUtils.consume(entity);

            System.out.println("app count = " + appInfoPList.getAppInfosCount());
            
            AppInfoP appInfoP = appInfoPList.getAppInfos(0);
            
            System.out.println("appInfoP at index 0 label: " + appInfoP.getLabel());
            System.out.println("appInfoP at index 0 package: " + appInfoP.getPackageName());
            System.out.println("appInfoP at index 0 apk path: " + appInfoP.getApkFilePath());
            System.out.println("appInfoP at index 0 apk size: " + appInfoP.getApkFileSize());

        } catch (ClientProtocolException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } finally {
            get.releaseConnection();
            client.getConnectionManager().shutdown();
        }
    }
    
}