package jpe.jni.test;

import java.nio.ByteBuffer;

import jpe.jni.Natives;

@Natives
public class TestNatives {

  static {
    System.loadLibrary("TestNatives");
  }

  public static void main(String[] args) {
    {
      testNatives1();
      System.out.println("testNatives1()");
    }
    {
      String result = testNatives2("hello"); 
      System.out.println("testNatives2(\"hello\"): " + result);
    }
    {
      float[] param = new float[] { 1.0f, 1.0f };
      testNatives3(param);
      System.out.println("testNatives3(\"hello\"): { " + param[0] + ", " + param[1] + " }");
    }
    {
      float[] param = new float[] { 1.0f, 1.0f };
      testNatives4(param);
      System.out.println("testNatives4(\"hello\"): { " + param[0] + ", " + param[1] + " }");
    }
    {
      ByteBuffer param = ByteBuffer.allocateDirect(2);
      param.put((byte)1);
      param.put((byte)1);
      testNatives5(param);
      param.rewind();
      System.out.println("testNatives4(\"hello\"): { " + param.get() + ", " + param.get() + " }");
    }
  }

  public static native void testNatives1();
  public static native String testNatives2(String param);
  public static native void testNatives3(float[] param);
  public static native void testNatives4(float[] paramIn);
  public static native void testNatives5(ByteBuffer param);
}