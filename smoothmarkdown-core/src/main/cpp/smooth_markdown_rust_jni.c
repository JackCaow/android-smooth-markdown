#include <jni.h>
#include <stdint.h>
#include <limits.h>
#include "smooth_markdown_rust.h"

JNIEXPORT jint JNICALL Java_com_jackcaow_smoothmarkdown_nativeparser_RustMarkdownBridge_abiVersionNative(JNIEnv *env, jobject self) {
    (void)env; (void)self;
    return (jint)smr_abi_version();
}

JNIEXPORT jbyteArray JNICALL Java_com_jackcaow_smoothmarkdown_nativeparser_RustMarkdownBridge_parseNative(JNIEnv *env, jobject self, jstring source, jint options) {
    (void)self;
    if (source == NULL) return NULL;
    jsize length = (*env)->GetStringLength(env, source);
    const jchar *units = (*env)->GetStringChars(env, source, NULL);
    if (units == NULL) return NULL;
    SmrBuffer output = {0};
    int32_t status = smr_parse_utf16((const uint16_t *)units, (size_t)length, (uint32_t)options, &output);
    (*env)->ReleaseStringChars(env, source, units);
    jbyteArray result = NULL;
    if (status == 0 && output.data != NULL && output.len <= INT_MAX) {
        result = (*env)->NewByteArray(env, (jsize)output.len);
        if (result != NULL) (*env)->SetByteArrayRegion(env, result, 0, (jsize)output.len, (const jbyte *)output.data);
    }
    smr_buffer_free(&output);
    return result;
}

#include <stdlib.h>
#include <string.h>
typedef struct ContextScope { jobject value; struct ContextScope *previous; } ContextScope;
typedef struct HostHooks { JNIEnv *env; jobject callbacks; jmethodID inline_method; jmethodID block_method;
    ContextScope *inline_scopes; ContextScope *block_scopes; } HostHooks;
static int32_t push_context(JNIEnv *env,ContextScope **stack,jobject value){
    ContextScope *scope=calloc(1,sizeof(ContextScope));if(!scope)return -1;
    scope->previous=*stack;*stack=scope;
    if(value&&!(*env)->ExceptionCheck(env))scope->value=(*env)->NewGlobalRef(env,value);
    return scope->value&&!(*env)->ExceptionCheck(env)?0:-1;
}
static void pop_context(JNIEnv *env,ContextScope **stack){
    if(!*stack)return;ContextScope *scope=*stack;*stack=scope->previous;
    if(scope->value)(*env)->DeleteGlobalRef(env,scope->value);free(scope);
}
static int32_t inline_context(void *ctx,const uint16_t *source,size_t length,int32_t begin){
    HostHooks *h=ctx;JNIEnv *env=h->env;if(!begin){pop_context(env,&h->inline_scopes);return 0;}
    jstring value=(!(*env)->ExceptionCheck(env)&&length<=INT_MAX)?(*env)->NewString(env,(const jchar *)source,(jsize)length):NULL;
    int32_t status=push_context(env,&h->inline_scopes,value);if(value)(*env)->DeleteLocalRef(env,value);return status;
}
static int32_t block_context(void *ctx,const uint16_t *const *lines,const size_t *lengths,size_t count,int32_t begin){
    HostHooks *h=ctx;JNIEnv *env=h->env;if(!begin){pop_context(env,&h->block_scopes);return 0;}
    if((*env)->ExceptionCheck(env)||count>INT_MAX)return push_context(env,&h->block_scopes,NULL);
    jclass cls=(*env)->FindClass(env,"java/lang/String");jobjectArray value=cls?(*env)->NewObjectArray(env,(jsize)count,cls,NULL):NULL;
    if(cls)(*env)->DeleteLocalRef(env,cls);
    for(size_t i=0;value&&i<count&&!(*env)->ExceptionCheck(env);i++){
        if(lengths[i]>INT_MAX){(*env)->DeleteLocalRef(env,value);value=NULL;break;}
        jstring line=(*env)->NewString(env,(const jchar *)lines[i],(jsize)lengths[i]);
        if(line){(*env)->SetObjectArrayElement(env,value,(jsize)i,line);(*env)->DeleteLocalRef(env,line);}
    }
    int32_t status=push_context(env,&h->block_scopes,value);if(value)(*env)->DeleteLocalRef(env,value);return status;
}
static int32_t match_result(JNIEnv *env, jlongArray values, SmrMatch *out) {
    if ((*env)->ExceptionCheck(env)) return -1;
    if (!values || (*env)->GetArrayLength(env,values)!=2) return 0;
    jlong result[2]; (*env)->GetLongArrayRegion(env,values,0,2,result);
    if ((*env)->ExceptionCheck(env)) return -1;
    if(result[0]<=0 || result[0]>UINT32_MAX || result[1]<=0 || result[1]>UINT32_MAX) return 0;
    out->consumed=(uint32_t)result[0];out->id=(uint32_t)result[1];return 1;
}
static int32_t inline_hook(void *ctx,const uint16_t *source,size_t length,uint32_t index,uint32_t absolute,SmrMatch *out) {
    HostHooks *h=ctx;JNIEnv *env=h->env;
    if ((*env)->ExceptionCheck(env)||length>INT_MAX) return -1;
    if ((*env)->PushLocalFrame(env,8)<0) return -1;
    (void)source;
    jstring text=h->inline_scopes?(jstring)h->inline_scopes->value:NULL;
    jlongArray values=text?(jlongArray)(*env)->CallObjectMethod(env,h->callbacks,h->inline_method,text,(jint)index,(jint)absolute):NULL;
    int32_t status=match_result(env,values,out);(*env)->PopLocalFrame(env,NULL);return status;
}
static int32_t block_hook(void *ctx,const uint16_t *const *lines,const size_t *lengths,size_t count,uint32_t index,uint32_t absolute,SmrMatch *out) {
    HostHooks *h=ctx;JNIEnv *env=h->env;
    if ((*env)->ExceptionCheck(env)||count>INT_MAX) return -1;
    if ((*env)->PushLocalFrame(env,8)<0) return -1;
    (void)lines;(void)lengths;
    jobjectArray text=h->block_scopes?(jobjectArray)h->block_scopes->value:NULL;
    jlongArray values=(text&&!(*env)->ExceptionCheck(env))?(jlongArray)(*env)->CallObjectMethod(env,h->callbacks,h->block_method,text,(jint)index,(jint)absolute):NULL;
    int32_t status=match_result(env,values,out);(*env)->PopLocalFrame(env,NULL);return status;
}
static int init_hooks(JNIEnv *env,jobject callbacks,HostHooks *host,SmrHooks *hooks){
    if(!callbacks)return 1;host->env=env;host->callbacks=callbacks;
    jclass cls=(*env)->GetObjectClass(env,callbacks);if(!cls)return 0;
    host->inline_method=(*env)->GetMethodID(env,cls,"inlineMatch","(Ljava/lang/String;II)[J");
    host->block_method=(*env)->ExceptionCheck(env)?NULL:(*env)->GetMethodID(env,cls,"blockMatch","([Ljava/lang/String;II)[J");
    (*env)->DeleteLocalRef(env,cls);if((*env)->ExceptionCheck(env))return 0;
    hooks->context=host;hooks->inline_callback=inline_hook;hooks->block_callback=block_hook;hooks->inline_context=inline_context;hooks->block_context=block_context;return 1;
}
static jbyteArray bytes_result(JNIEnv *env,SmrBuffer *out,int32_t status){
    jbyteArray result=NULL;
    if(status==0&&!(*env)->ExceptionCheck(env)&&out->len<=INT_MAX){result=(*env)->NewByteArray(env,(jsize)out->len);if(result)(*env)->SetByteArrayRegion(env,result,0,(jsize)out->len,(const jbyte *)out->data);}
    smr_buffer_free(out);return result;
}
static jstring html_result(JNIEnv *env,SmrBuffer *out,int32_t status){
    jstring result=NULL;
    if(status==0&&!(*env)->ExceptionCheck(env)&&out->len%2==0&&out->len/2<=INT_MAX){
        /* All supported Android/host targets are little endian; copy avoids alignment assumptions. */
        size_t count=out->len/2;jchar *units=malloc(count?count*sizeof(jchar):sizeof(jchar));
        if(units){for(size_t i=0;i<count;i++)units[i]=(jchar)(out->data[2*i]|((uint16_t)out->data[2*i+1]<<8));result=(*env)->NewString(env,units,(jsize)count);free(units);}
    }smr_buffer_free(out);return result;
}
JNIEXPORT jbyteArray JNICALL Java_com_jackcaow_smoothmarkdown_nativeparser_RustMarkdownBridge_parseWithHooksNative(JNIEnv *env,jobject self,jstring source,jint flags,jobject callbacks){
    (void)self;if(!source)return NULL;HostHooks host={0};SmrHooks hooks={0};if(!init_hooks(env,callbacks,&host,&hooks))return NULL;
    jsize length=(*env)->GetStringLength(env,source);const jchar *units=(*env)->GetStringChars(env,source,NULL);if(!units)return NULL;
    SmrBuffer out={0};int32_t status=smr_parse_with_hooks_utf16(units,(size_t)length,(uint32_t)flags,callbacks?&hooks:NULL,&out);
    (*env)->ReleaseStringChars(env,source,units);while(host.inline_scopes)pop_context(env,&host.inline_scopes);while(host.block_scopes)pop_context(env,&host.block_scopes);return bytes_result(env,&out,status);
}
typedef struct RefBytes {uint8_t *data;size_t len,capacity;} RefBytes;
static int append(RefBytes *b,const void *data,size_t len){if(len>64*1024*1024||b->len>64*1024*1024-len)return 0;size_t needed=b->len+len;if(needed>b->capacity){size_t cap=needed*2;uint8_t *next=realloc(b->data,cap);if(!next)return 0;b->data=next;b->capacity=cap;}memcpy(b->data+b->len,data,len);b->len=needed;return 1;}
static int word(RefBytes *b,uint32_t n){uint8_t bytes[4]={(uint8_t)n,(uint8_t)(n>>8),(uint8_t)(n>>16),(uint8_t)(n>>24)};return append(b,bytes,4);}
static int references(JNIEnv *env,jobjectArray triples,RefBytes *b){
    jsize count=triples?(*env)->GetArrayLength(env,triples):0;if(count%3)return 0;if(!append(b,"SMF1",4)||!word(b,(uint32_t)(count/3)))return 0;
    for(jsize i=0;i<count;i++){
        jstring text=(jstring)(*env)->GetObjectArrayElement(env,triples,i);
        if((*env)->ExceptionCheck(env))return 0;
        if(!text){if(i%3!=2||!word(b,UINT32_MAX))return 0;continue;}
        jsize length=(*env)->GetStringLength(env,text);const jchar *units=(*env)->GetStringChars(env,text,NULL);
        int ok=units&&word(b,(uint32_t)length);for(jsize j=0;ok&&j<length;j++){uint8_t bytes[2]={(uint8_t)units[j],(uint8_t)(units[j]>>8)};ok=append(b,bytes,2);}
        if(units)(*env)->ReleaseStringChars(env,text,units);(*env)->DeleteLocalRef(env,text);if(!ok||(*env)->ExceptionCheck(env))return 0;
    }return 1;
}
JNIEXPORT jbyteArray JNICALL Java_com_jackcaow_smoothmarkdown_nativeparser_RustMarkdownBridge_parseInlineNative(JNIEnv *env,jobject self,jstring source,jint flags,jobjectArray triples,jobject callbacks){
    (void)self;if(!source)return NULL;HostHooks host={0};SmrHooks hooks={0};if(!init_hooks(env,callbacks,&host,&hooks))return NULL;
    RefBytes refs={0};if(!references(env,triples,&refs)){free(refs.data);return NULL;}
    jsize length=(*env)->GetStringLength(env,source);const jchar *units=(*env)->GetStringChars(env,source,NULL);if(!units){free(refs.data);return NULL;}
    SmrBuffer out={0};int32_t status=smr_parse_inline_utf16(units,(size_t)length,(uint32_t)flags,refs.data,refs.len,callbacks?&hooks:NULL,&out);
    (*env)->ReleaseStringChars(env,source,units);free(refs.data);while(host.inline_scopes)pop_context(env,&host.inline_scopes);while(host.block_scopes)pop_context(env,&host.block_scopes);return bytes_result(env,&out,status);
}
JNIEXPORT jstring JNICALL Java_com_jackcaow_smoothmarkdown_nativeparser_RustMarkdownBridge_renderWireNative(JNIEnv *env,jobject self,jbyteArray ast,jboolean escape){
    (void)self;if(!ast)return NULL;jsize length=(*env)->GetArrayLength(env,ast);jbyte *bytes=(*env)->GetByteArrayElements(env,ast,NULL);if(!bytes)return NULL;
    SmrBuffer out={0};int32_t status=smr_render_ast_utf16((uint8_t *)bytes,(size_t)length,escape?4:0,&out);(*env)->ReleaseByteArrayElements(env,ast,bytes,JNI_ABORT);return html_result(env,&out,status);
}
JNIEXPORT jstring JNICALL Java_com_jackcaow_smoothmarkdown_nativeparser_RustMarkdownBridge_exportSourceNative(JNIEnv *env,jobject self,jstring source,jint flags,jboolean escape){
    (void)self;if(!source)return NULL;jsize length=(*env)->GetStringLength(env,source);const jchar *units=(*env)->GetStringChars(env,source,NULL);if(!units)return NULL;
    SmrBuffer out={0};int32_t status=smr_export_html_utf16(units,(size_t)length,(uint32_t)flags|(escape?4:0),&out);(*env)->ReleaseStringChars(env,source,units);return html_result(env,&out,status);
}
