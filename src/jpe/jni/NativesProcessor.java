package jpe.jni;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Set;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Filer;
import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.tools.Diagnostic;
import javax.tools.FileObject;
import javax.tools.StandardLocation;

@SupportedAnnotationTypes(value = {"jpe.jni.Natives"})
@SupportedSourceVersion(SourceVersion.RELEASE_6)
public class NativesProcessor extends AbstractProcessor {

  public static final boolean DEBUG = false;

  private Filer filer;
  private Messager messager;
  private boolean needsHintBuffer = false;
  private StringBuilder glueBuilder = new StringBuilder();
  private StringBuilder glueHintBuilder = new StringBuilder();
  private StringBuilder glueDeclBuilder = new StringBuilder();
  private StringBuilder gluePreCallBuilder = new StringBuilder();
  private StringBuilder glueBodyBuilder = new StringBuilder();
  private StringBuilder gluePostCallBuilder = new StringBuilder();
  private Natives classAnnotation;
  private TypeElement classElement;
  private String className;
  private String classType;
  private ElementKind classKind;
  private Element memberElement;
  private String memberName;
  private String memberType;
  private ElementKind memberKind;

  @Override
  public void init(ProcessingEnvironment processingEnvironment) {
    filer = processingEnvironment.getFiler();
    messager = processingEnvironment.getMessager();
  }

  @Override
  public boolean process(Set<? extends TypeElement> elements, RoundEnvironment roundEnvironment) {
    for (TypeElement typeElement : elements) {
      log("process: type element name: " + typeElement.getSimpleName() + ", kind: " + typeElement.getKind());
      for (Element element : roundEnvironment.getElementsAnnotatedWith(typeElement)) {
        try {
          classAnnotation = element.getAnnotation(Natives.class);
          processSource(element);
        } catch (IOException e) {
        }
      }
    }
    return true;
  }

  private void processSource(Element element) throws IOException {
    classElement = (TypeElement) element;
    className = classElement.getSimpleName().toString();
    classType = classElement.asType().toString();
    classKind = classElement.getKind();
    log("processSource: class element name: " + className + ", kind: " + classKind + ", type: " + classType);
    FileObject glueFile = filer.createResource(StandardLocation.SOURCE_OUTPUT, "", className + ".inc");
    PrintWriter glueFileWriter = new PrintWriter(new BufferedWriter((glueFile.openWriter())));
    // only process direct (non-inherited) members
//  for (Element childElement : elements.getAllMembers(classElement)) {
    for (Element childElement : classElement.getEnclosedElements()) {
      memberElement = childElement;
      memberName = memberElement.getSimpleName().toString();
      memberType = memberElement.asType().toString();
      memberKind = memberElement.getKind();
      log("member name: " + memberName + ", type: " + memberType + ", kind: " + memberKind);
      if (memberKind == ElementKind.METHOD && memberElement.getModifiers().contains(Modifier.NATIVE)) {
        processMethod();
      }
      glueDeclBuilder.setLength(0);
      gluePreCallBuilder.setLength(0);
      glueBodyBuilder.setLength(0);
      gluePostCallBuilder.setLength(0);
    }
    if (needsHintBuffer) {
      glueFileWriter.print("//typedef struct {\n");
      glueFileWriter.print("//  void *address;\n");
      glueFileWriter.print("//  jlong capacity;\n");
      glueFileWriter.print("//} __buffer_t;\n\n");
    }
    glueFileWriter.print(glueHintBuilder.toString());
    glueFileWriter.print(glueBuilder.toString());
    glueFileWriter.flush();
    glueFileWriter.close();
    needsHintBuffer = false;
    glueHintBuilder.setLength(0);
    glueBuilder.setLength(0);
  }

  public void processMethod() {
    ExecutableElement executableElement = (ExecutableElement) memberElement;
    String returnType = executableElement.getReturnType().toString();
    String nativeReturnType = getNativeType(returnType);
    String nativeName = memberName;
    String hintReturnType = nativeReturnType;
    if (!nativeReturnType.equals("void")) {
      if (nativeReturnType.equals("jstring")) {
        hintReturnType = "std::string";
      } else if (returnType.equals("java.nio.Buffer") || returnType.equals("java.nio.ByteBuffer")) {
        hintReturnType = "const __buffer_t";
        needsHintBuffer = true;
      } else {
        hintReturnType = nativeReturnType;
      }
    }
    glueHintBuilder.append("//static inline " + hintReturnType + " " + nativeName + "(");
    glueDeclBuilder.append("extern \"C\" JNIEXPORT " + nativeReturnType + " JNICALL Java_"
        + classElement.getQualifiedName().toString().replace('.', '_') + "_" + nativeName + "(JNIEnv *env, jclass c");
    glueBodyBuilder.append("  ");
    if (!nativeReturnType.equals("void")) {
      if (nativeReturnType.equals("jstring")) {
        glueBodyBuilder.append("std::string result = ");
      } else if (returnType.equals("java.nio.Buffer") || returnType.equals("java.nio.ByteBuffer")) {
        glueBodyBuilder.append("const __buffer_t result = ");
      } else {
        glueBodyBuilder.append(nativeReturnType + " result = (" + nativeReturnType + ") ");
      }
    }
    glueBodyBuilder.append(memberName + "(");
    boolean first = true;
    for (VariableElement parameterElement : executableElement.getParameters()) {
      NativeParameter nativeParameter = getNativeParameter(parameterElement);
      if (first) {
        first = false;
      } else {
        glueBodyBuilder.append(", ");
        glueHintBuilder.append(", ");
      }
      glueHintBuilder.append(nativeParameter.glueHint);
      glueDeclBuilder.append(", ");
      glueDeclBuilder.append(nativeParameter.glueDecl);
      gluePreCallBuilder.append(nativeParameter.gluePreCall);
      glueBodyBuilder.append(nativeParameter.glueCall);
      gluePostCallBuilder.append(nativeParameter.gluePostCall);
    }
    glueHintBuilder.append(") {\n  \n//}\n\n");
    glueDeclBuilder.append(") {\n");
    glueBodyBuilder.append(");\n");
    if (!nativeReturnType.equals("void")) {
      if (nativeReturnType.equals("jstring")) {
        gluePostCallBuilder.append("  return env->NewStringUTF(result.c_str());\n");
      } else if (returnType.equals("java.nio.Buffer") || returnType.equals("java.nio.ByteBuffer")) {
        glueBodyBuilder.append("  return env->NewDirectByteBuffer(result.address, result.capacity);\n");
      } else {
        gluePostCallBuilder.append("  return result;\n");
      }
    }
    glueBuilder.append(glueDeclBuilder.toString());
    glueBuilder.append(gluePreCallBuilder.toString());
    glueBuilder.append(glueBodyBuilder.toString());
    glueBuilder.append(gluePostCallBuilder.toString());
    glueBuilder.append("}\n\n");
  }

  private class NativeParameter {
    String glueDecl;
    String glueHint;
    String gluePreCall;
    String glueCall;
    String gluePostCall;
  }

  private NativeParameter getNativeParameter(VariableElement parameterElement) {
    String parameterName = parameterElement.getSimpleName().toString();
    String parameterType = parameterElement.asType().toString();
    boolean isIn = (parameterElement.getAnnotation(NativeIn.class) != null);
    log("getNativeParameter: parameter name: " + parameterName + ", type: " + parameterType);
    NativeParameter parameter = new NativeParameter();
    String nativeType = getNativeType(parameterType);
    parameter.glueDecl = nativeType + " " + parameterName;
    if (nativeType.equals("jbyteArray") || nativeType.equals("jcharArray") || nativeType.equals("jintArray") || nativeType.equals("jlongArray")
        || nativeType.equals("jshortArray") || nativeType.equals("jbooleanArray") || nativeType.equals("jfloatArray")
        || nativeType.equals("jdoubleArray")) {
      String internalType = nativeType.substring(0, nativeType.length() - 5);
      String internalTypeUpper = Character.toUpperCase(internalType.charAt(1)) + internalType.substring(2);
      parameter.glueHint = internalType + " *" + parameterName;
      parameter.gluePreCall = "  " + internalType + " *" + parameterName + "N = ";
      parameter.gluePreCall += "(" + parameterName + " != NULL) ? ";
      parameter.gluePreCall += "env->Get" + internalTypeUpper + "ArrayElements(" + parameterName + ", NULL)";
      parameter.gluePreCall += " : NULL;\n";
      parameter.glueCall = parameterName + "N";
      String releaseOption = isIn ? "JNI_ABORT" : "0";
      parameter.gluePostCall = "  if (" + parameterName + " != NULL) ";
      parameter.gluePostCall +=
          "env->Release" + internalTypeUpper + "ArrayElements(" + parameterName + ", " + parameterName + "N" + " , " + releaseOption + ");\n";
    } else if (nativeType.equals("jstring")) {
      parameter.glueHint = "const char *" + parameterName;
      parameter.gluePreCall = "  const char *" + parameterName + "N = ";
      parameter.gluePreCall += "(" + parameterName + " != NULL) ? ";
      parameter.gluePreCall += "env->GetStringUTFChars(" + parameterName + ", (jboolean *) 0)";
      parameter.gluePreCall += " : NULL;\n";
      parameter.glueCall = parameterName + "N";
      parameter.gluePostCall = "  if (" + parameterName + " != NULL) ";
      parameter.gluePostCall += "env->ReleaseStringUTFChars(" + parameterName + ", " + parameterName + "N);\n";
    } else if (parameterType.equals("java.nio.Buffer") || parameterType.equals("java.nio.ByteBuffer")) {
      parameter.glueHint = "unsigned char *" + parameterName;
      parameter.gluePreCall = "  unsigned char *" + parameterName + "N = ";
      parameter.gluePreCall += "(" + parameterName + " != NULL) ? ";
      parameter.gluePreCall += "(unsigned char *) env->GetDirectBufferAddress(" + parameterName + ")";
      parameter.gluePreCall += " : NULL;\n";
      parameter.glueCall = parameterName + "N";
      parameter.gluePostCall = "";
    } else {
      parameter.glueHint = parameter.glueDecl;
      parameter.gluePreCall = "";
      parameter.glueCall = parameterName;
      parameter.gluePostCall = "";
    }
    return parameter;
  }

  private static String getNativeType(String javaType) {
    if (javaType.equals("void")) {
      return "void";
    } else if (javaType.equals("int")) {
      return "jint";
    } else if (javaType.equals("boolean")) {
      return "jboolean";
    } else if (javaType.equals("float")) {
      return "jfloat";
    } else if (javaType.equals("long")) {
      return "jlong";
    } else if (javaType.equals("byte[]")) {
      return "jbyteArray";
    } else if (javaType.equals("char[]")) {
      return "jcharArray";
    } else if (javaType.equals("short[]")) {
      return "jshortArray";
    } else if (javaType.equals("int[]")) {
      return "jintArray";
    } else if (javaType.equals("long[]")) {
      return "jlongArray";
    } else if (javaType.equals("boolean[]")) {
      return "jbooleanArray";
    } else if (javaType.equals("float[]")) {
      return "jfloatArray";
    } else if (javaType.equals("double[]")) {
      return "jdoubleArray";
    } else if (javaType.equals("java.lang.String")) {
      return "jstring";
    } else if (javaType.equals("java.nio.Buffer") || javaType.equals("java.nio.ByteBuffer")) {
      return "jobject";
    }
    return "??" + javaType + "??";
  }

  private void log(String message) {
    if (DEBUG)
      messager.printMessage(Diagnostic.Kind.NOTE, "NativesProcessor: " + message);
  }
}