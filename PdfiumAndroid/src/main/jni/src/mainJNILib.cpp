#include "util.hpp"

extern "C" {
    #include <unistd.h>
    #include <sys/mman.h>
    #include <sys/stat.h>
    #include <errno.h>
    #include <string.h>
    #include <stdio.h>
    #include <stdlib.h>
    #include <math.h>
    #include <time.h>
}

#include <android/native_window.h>
#include <android/native_window_jni.h>
#include <android/bitmap.h>
#include <utils/Mutex.h>
using namespace android;

#include <fpdfview.h>
#include <fpdf_doc.h>
#include <fpdf_text.h>
#include <fpdf_annot.h>
#include <fpdf_formfill.h>
#include <fpdf_edit.h>
#include <fpdf_save.h>
#include <string>

#include <vector>

static Mutex sLibraryLock;

static int sLibraryReferenceCount = 0;

static std::vector<int> searchResultAnnotIndexes;

struct FdFileWrite {
    FPDF_FILEWRITE fileWrite;
    int fd;
};

static int writeBlockToFd(FPDF_FILEWRITE* fileWrite, const void* data, unsigned long size) {
    FdFileWrite* fdFileWrite = reinterpret_cast<FdFileWrite*>(fileWrite);
    const char* buffer = static_cast<const char*>(data);
    unsigned long remaining = size;
    while (remaining > 0) {
        ssize_t written = write(fdFileWrite->fd, buffer, remaining);
        if (written < 0) {
            if (errno == EINTR) {
                continue;
            }
            LOGE("Failed to write PDF save block. Error:%d", errno);
            return 0;
        }
        if (written == 0) {
            return 0;
        }
        buffer += written;
        remaining -= written;
    }
    return 1;
}

static bool setAnnotWideString(JNIEnv* env, FPDF_ANNOTATION annot, const char* key, jstring value) {
    if (value == NULL) {
        return true;
    }
    const jchar* chars = env->GetStringChars(value, NULL);
    if (chars == NULL) {
        return false;
    }
    jsize length = env->GetStringLength(value);
    std::vector<FPDF_WCHAR> wide(length + 1);
    for (jsize i = 0; i < length; i++) {
        wide[i] = static_cast<FPDF_WCHAR>(chars[i]);
    }
    wide[length] = 0;
    FPDF_BOOL result = FPDFAnnot_SetStringValue(annot, key, wide.data());
    env->ReleaseStringChars(value, chars);
    return result;
}

static std::vector<FPDF_WCHAR> getAnnotWideString(FPDF_ANNOTATION annot, const char* key);

static bool setAnnotAsciiString(FPDF_ANNOTATION annot, const char* key, const char* value) {
    std::vector<FPDF_WCHAR> wide;
    while (*value != '\0') {
        wide.push_back(static_cast<unsigned char>(*value));
        value++;
    }
    wide.push_back(0);
    return FPDFAnnot_SetStringValue(annot, key, wide.data());
}

static std::string colorToHex(unsigned int red, unsigned int green, unsigned int blue) {
    char buffer[7];
    snprintf(buffer, sizeof(buffer), "%02X%02X%02X", red & 0xFF, green & 0xFF, blue & 0xFF);
    return std::string(buffer);
}

static bool hexDigitValue(FPDF_WCHAR digit, unsigned int* value) {
    if (digit >= '0' && digit <= '9') {
        *value = digit - '0';
        return true;
    }
    if (digit >= 'A' && digit <= 'F') {
        *value = digit - 'A' + 10;
        return true;
    }
    if (digit >= 'a' && digit <= 'f') {
        *value = digit - 'a' + 10;
        return true;
    }
    return false;
}

static bool parseHexColor(const std::vector<FPDF_WCHAR>& value,
                          unsigned int* red,
                          unsigned int* green,
                          unsigned int* blue) {
    if (value.size() != 6) {
        return false;
    }

    unsigned int components[3];
    for (int i = 0; i < 3; i++) {
        unsigned int high;
        unsigned int low;
        if (!hexDigitValue(value[i * 2], &high) || !hexDigitValue(value[i * 2 + 1], &low)) {
            return false;
        }
        components[i] = (high << 4) | low;
    }
    *red = components[0];
    *green = components[1];
    *blue = components[2];
    return true;
}

static bool getAnnotStoredColor(FPDF_ANNOTATION annot,
                                unsigned int* red,
                                unsigned int* green,
                                unsigned int* blue) {
    return parseHexColor(getAnnotWideString(annot, "MJColor"), red, green, blue);
}

static bool isPdfTokenDelimiter(char value) {
    return value == ' ' || value == '\n' || value == '\r' || value == '\t'
            || value == '[' || value == ']' || value == '(' || value == ')'
            || value == '<' || value == '>' || value == '{' || value == '}';
}

static bool parseDoubleToken(const std::string& token, double* value) {
    if (token.empty()) {
        return false;
    }
    char* end = NULL;
    double parsed = strtod(token.c_str(), &end);
    if (end == token.c_str() || *end != '\0') {
        return false;
    }
    *value = parsed;
    return true;
}

static unsigned int colorComponentToByte(double component) {
    double scaled = component <= 1.0 ? component * 255.0 : component;
    if (scaled < 0.0) {
        scaled = 0.0;
    }
    if (scaled > 255.0) {
        scaled = 255.0;
    }
    return static_cast<unsigned int>(round(scaled));
}

static bool setColorFromRecentNumbers(const std::vector<double>& recentNumbers,
                                      unsigned int* red,
                                      unsigned int* green,
                                      unsigned int* blue,
                                      unsigned int* alpha) {
    if (recentNumbers.size() < 3) {
        return false;
    }
    size_t start = recentNumbers.size() - 3;
    *red = colorComponentToByte(recentNumbers[start]);
    *green = colorComponentToByte(recentNumbers[start + 1]);
    *blue = colorComponentToByte(recentNumbers[start + 2]);
    *alpha = 255;
    return true;
}

static bool updateColorFromAppearanceToken(const std::string& token,
                                           const std::vector<double>& recentNumbers,
                                           unsigned int* red,
                                           unsigned int* green,
                                           unsigned int* blue,
                                           unsigned int* alpha) {
    if (token == "rg" || token == "RG" || token == "sc" || token == "SC"
            || token == "scn" || token == "SCN") {
        return setColorFromRecentNumbers(recentNumbers, red, green, blue, alpha);
    }
    if ((token == "g" || token == "G") && !recentNumbers.empty()) {
        unsigned int gray = colorComponentToByte(recentNumbers.back());
        *red = gray;
        *green = gray;
        *blue = gray;
        *alpha = 255;
        return true;
    }
    if ((token == "k" || token == "K") && recentNumbers.size() >= 4) {
        size_t start = recentNumbers.size() - 4;
        double cyan = recentNumbers[start];
        double magenta = recentNumbers[start + 1];
        double yellow = recentNumbers[start + 2];
        double black = recentNumbers[start + 3];
        *red = colorComponentToByte(1.0 - fmin(1.0, cyan + black));
        *green = colorComponentToByte(1.0 - fmin(1.0, magenta + black));
        *blue = colorComponentToByte(1.0 - fmin(1.0, yellow + black));
        *alpha = 255;
        return true;
    }
    return false;
}

static bool getAnnotAppearanceStreamColor(FPDF_ANNOTATION annot,
                                          unsigned int* red,
                                          unsigned int* green,
                                          unsigned int* blue,
                                          unsigned int* alpha) {
    unsigned long bytes = FPDFAnnot_GetAP(annot, FPDF_ANNOT_APPEARANCEMODE_NORMAL, NULL, 0);
    if (bytes <= sizeof(FPDF_WCHAR)) {
        return false;
    }

    unsigned long bufferBytes = bytes + sizeof(FPDF_WCHAR);
    std::vector<FPDF_WCHAR> wide(bufferBytes / sizeof(FPDF_WCHAR));
    unsigned long copied = FPDFAnnot_GetAP(annot, FPDF_ANNOT_APPEARANCEMODE_NORMAL,
                                           wide.data(), bufferBytes);
    if (copied == 0 || copied > bufferBytes) {
        return false;
    }

    std::vector<double> recentNumbers;
    std::string token;
    bool foundColor = false;
    size_t length = copied / sizeof(FPDF_WCHAR);
    for (size_t i = 0; i <= length; i++) {
        char value = '\0';
        if (i < length && wide[i] != 0 && wide[i] <= 0x7F) {
            value = static_cast<char>(wide[i]);
        }
        if (i == length || value == '\0' || isPdfTokenDelimiter(value)) {
            if (!token.empty()) {
                double number;
                if (parseDoubleToken(token, &number)) {
                    recentNumbers.push_back(number);
                    if (recentNumbers.size() > 8) {
                        recentNumbers.erase(recentNumbers.begin());
                    }
                } else if (updateColorFromAppearanceToken(token, recentNumbers,
                                                          red, green, blue, alpha)) {
                    foundColor = true;
                    recentNumbers.clear();
                } else {
                    recentNumbers.clear();
                }
                token.clear();
            }
        } else {
            token.push_back(value);
        }
    }
    return foundColor;
}

static bool getAnnotAppearanceColor(FPDF_ANNOTATION annot,
                                    unsigned int* red,
                                    unsigned int* green,
                                    unsigned int* blue,
                                    unsigned int* alpha) {
    int objectCount = FPDFAnnot_GetObjectCount(annot);
    for (int i = 0; i < objectCount; i++) {
        FPDF_PAGEOBJECT object = FPDFAnnot_GetObject(annot, i);
        if (object == NULL) {
            continue;
        }
        unsigned int objectRed;
        unsigned int objectGreen;
        unsigned int objectBlue;
        unsigned int objectAlpha;
        if (FPDFPageObj_GetFillColor(object, &objectRed, &objectGreen, &objectBlue, &objectAlpha)
                && objectAlpha > 0) {
            *red = objectRed;
            *green = objectGreen;
            *blue = objectBlue;
            *alpha = objectAlpha;
            return true;
        }
        if (FPDFPageObj_GetStrokeColor(object, &objectRed, &objectGreen, &objectBlue, &objectAlpha)
                && objectAlpha > 0) {
            *red = objectRed;
            *green = objectGreen;
            *blue = objectBlue;
            *alpha = objectAlpha;
            return true;
        }
    }
    return getAnnotAppearanceStreamColor(annot, red, green, blue, alpha);
}

static bool getHighlightAnnotationColor(FPDF_ANNOTATION annot,
                                        unsigned int* red,
                                        unsigned int* green,
                                        unsigned int* blue,
                                        unsigned int* alpha) {
    if (getAnnotStoredColor(annot, red, green, blue)) {
        *alpha = 255;
        return true;
    }
    if (getAnnotAppearanceColor(annot, red, green, blue, alpha)) {
        return true;
    }
    return FPDFAnnot_GetColor(annot, FPDFANNOT_COLORTYPE_Color, red, green, blue, alpha);
}

static std::string makeAnnotName() {
    static unsigned long counter = 0;
    struct timespec time;
    clock_gettime(CLOCK_REALTIME, &time);
    char buffer[96];
    snprintf(buffer, sizeof(buffer), "MJPDF-%lld-%lld-%lu",
             static_cast<long long>(time.tv_sec),
             static_cast<long long>(time.tv_nsec),
             ++counter);
    return std::string(buffer);
}

static std::vector<FPDF_WCHAR> jstringToWide(JNIEnv* env, jstring value) {
    std::vector<FPDF_WCHAR> wide;
    if (value == NULL) {
        return wide;
    }
    const jchar* chars = env->GetStringChars(value, NULL);
    if (chars == NULL) {
        return wide;
    }
    jsize length = env->GetStringLength(value);
    wide.reserve(length);
    for (jsize i = 0; i < length; i++) {
        wide.push_back(static_cast<FPDF_WCHAR>(chars[i]));
    }
    env->ReleaseStringChars(value, chars);
    return wide;
}

static std::vector<FPDF_WCHAR> getAnnotWideString(FPDF_ANNOTATION annot, const char* key) {
    std::vector<FPDF_WCHAR> empty;
    unsigned long bytes = FPDFAnnot_GetStringValue(annot, key, NULL, 0);
    if (bytes <= sizeof(FPDF_WCHAR)) {
        return empty;
    }

    unsigned long bufferBytes = bytes + sizeof(FPDF_WCHAR);
    std::vector<FPDF_WCHAR> value(bufferBytes / sizeof(FPDF_WCHAR));
    unsigned long copied = FPDFAnnot_GetStringValue(annot, key, value.data(), bufferBytes);
    if (copied == 0 || copied > bufferBytes) {
        return empty;
    }
    value.resize(copied / sizeof(FPDF_WCHAR));
    if (!value.empty() && value.back() == 0) {
        value.pop_back();
    }
    return value;
}

static bool wideEqualsAscii(const std::vector<FPDF_WCHAR>& wide, const char* ascii) {
    size_t length = strlen(ascii);
    if (wide.size() != length) {
        return false;
    }
    for (size_t i = 0; i < length; i++) {
        if (wide[i] != static_cast<unsigned char>(ascii[i])) {
            return false;
        }
    }
    return true;
}

static bool wideEquals(const std::vector<FPDF_WCHAR>& left, const std::vector<FPDF_WCHAR>& right) {
    if (left.size() != right.size()) {
        return false;
    }
    for (size_t i = 0; i < left.size(); i++) {
        if (left[i] != right[i]) {
            return false;
        }
    }
    return true;
}

static jstring wideToJString(JNIEnv* env, const std::vector<FPDF_WCHAR>& wide) {
    if (wide.empty()) {
        return env->NewStringUTF("");
    }
    std::vector<jchar> chars(wide.size());
    for (size_t i = 0; i < wide.size(); i++) {
        chars[i] = static_cast<jchar>(wide[i]);
    }
    return env->NewString(chars.data(), chars.size());
}

static bool isSearchResultAnnotIndex(int index) {
    for (std::vector<int>::const_iterator it = searchResultAnnotIndexes.begin();
         it != searchResultAnnotIndexes.end(); ++it) {
        if (*it == index) {
            return true;
        }
    }
    return false;
}

static bool isAppHighlightAnnotation(FPDF_ANNOTATION annot) {
    if (annot == NULL || FPDFAnnot_GetSubtype(annot) != FPDF_ANNOT_HIGHLIGHT) {
        return false;
    }
    std::vector<FPDF_WCHAR> group = getAnnotWideString(annot, "MJGroup");
    if (!group.empty()) {
        return true;
    }
    return wideEqualsAscii(getAnnotWideString(annot, "T"), "MJ PDF");
}

static FS_RECTF normalizedRect(FS_RECTF rect) {
    FS_RECTF normalized;
    normalized.left = fmin(rect.left, rect.right);
    normalized.right = fmax(rect.left, rect.right);
    normalized.bottom = fmin(rect.bottom, rect.top);
    normalized.top = fmax(rect.bottom, rect.top);
    return normalized;
}

static bool rectContainsPoint(FS_RECTF rect, float x, float y, float tolerance) {
    FS_RECTF normalized = normalizedRect(rect);
    return x >= normalized.left - tolerance
            && x <= normalized.right + tolerance
            && y >= normalized.bottom - tolerance
            && y <= normalized.top + tolerance;
}

static void unionRect(FS_RECTF* target, FS_RECTF rect) {
    FS_RECTF normalized = normalizedRect(rect);
    target->left = fmin(target->left, normalized.left);
    target->right = fmax(target->right, normalized.right);
    target->bottom = fmin(target->bottom, normalized.bottom);
    target->top = fmax(target->top, normalized.top);
}

static void initLibraryIfNeed(){
    Mutex::Autolock lock(sLibraryLock);
    if(sLibraryReferenceCount == 0){
        LOGD("Init FPDF library");
        FPDF_InitLibrary();
    }
    sLibraryReferenceCount++;
}

static void destroyLibraryIfNeed(){
    Mutex::Autolock lock(sLibraryLock);
    sLibraryReferenceCount--;
    if(sLibraryReferenceCount == 0){
        LOGD("Destroy FPDF library");
        FPDF_DestroyLibrary();
    }
}

struct rgb {
    uint8_t red;
    uint8_t green;
    uint8_t blue;
};

class DocumentFile {
    private:
    int fileFd;

    public:
    FPDF_DOCUMENT pdfDocument = NULL;
    size_t fileSize;

    DocumentFile() { initLibraryIfNeed(); }
    ~DocumentFile();
};
DocumentFile::~DocumentFile(){
    if(pdfDocument != NULL){
        FPDF_CloseDocument(pdfDocument);
    }

    destroyLibraryIfNeed();
}

template <class string_type>
inline typename string_type::value_type* WriteInto(string_type* str, size_t length_with_null) {
  str->reserve(length_with_null);
  str->resize(length_with_null - 1);
  return &((*str)[0]);
}

inline long getFileSize(int fd){
    struct stat file_state;

    if(fstat(fd, &file_state) >= 0){
        return (long)(file_state.st_size);
    }else{
        LOGE("Error getting file size");
        return 0;
    }
}

static char* getErrorDescription(const long error) {
    char* description = NULL;
    switch(error) {
        case FPDF_ERR_SUCCESS:
            asprintf(&description, "No error.");
            break;
        case FPDF_ERR_FILE:
            asprintf(&description, "File not found or could not be opened.");
            break;
        case FPDF_ERR_FORMAT:
            asprintf(&description, "File not in PDF format or corrupted.");
            break;
        case FPDF_ERR_PASSWORD:
            asprintf(&description, "Incorrect password.");
            break;
        case FPDF_ERR_SECURITY:
            asprintf(&description, "Unsupported security scheme.");
            break;
        case FPDF_ERR_PAGE:
            asprintf(&description, "Page not found or content error.");
            break;
        default:
            asprintf(&description, "Unknown error.");
    }

    return description;
}

int jniThrowException(JNIEnv* env, const char* className, const char* message) {
    jclass exClass = env->FindClass(className);
    if (exClass == NULL) {
        LOGE("Unable to find exception class %s", className);
        return -1;
    }

    if(env->ThrowNew(exClass, message ) != JNI_OK) {
        LOGE("Failed throwing '%s' '%s'", className, message);
        return -1;
    }

    return 0;
}

int jniThrowExceptionFmt(JNIEnv* env, const char* className, const char* fmt, ...) {
    va_list args;
    va_start(args, fmt);
    char msgBuf[512];
    vsnprintf(msgBuf, sizeof(msgBuf), fmt, args);
    return jniThrowException(env, className, msgBuf);
    va_end(args);
}

jobject NewLong(JNIEnv* env, jlong value) {
    jclass cls = env->FindClass("java/lang/Long");
    jmethodID methodID = env->GetMethodID(cls, "<init>", "(J)V");
    return env->NewObject(cls, methodID, value);
}

jobject NewInteger(JNIEnv* env, jint value) {
    jclass cls = env->FindClass("java/lang/Integer");
    jmethodID methodID = env->GetMethodID(cls, "<init>", "(I)V");
    return env->NewObject(cls, methodID, value);
}

uint16_t rgbTo565(rgb *color) {
    return ((color->red >> 3) << 11) | ((color->green >> 2) << 5) | (color->blue >> 3);
}

void rgbBitmapTo565(void *source, int sourceStride, void *dest, AndroidBitmapInfo *info) {
    rgb *srcLine;
    uint16_t *dstLine;
    int y, x;
    for (y = 0; y < info->height; y++) {
        srcLine = (rgb*) source;
        dstLine = (uint16_t*) dest;
        for (x = 0; x < info->width; x++) {
            dstLine[x] = rgbTo565(&srcLine[x]);
        }
        source = (char*) source + sourceStride;
        dest = (char*) dest + info->stride;
    }
}

extern "C" { //For JNI support

static int getBlock(void* param, unsigned long position, unsigned char* outBuffer,
        unsigned long size) {
    const int fd = reinterpret_cast<intptr_t>(param);
    const int readCount = pread(fd, outBuffer, size, position);
    if (readCount < 0) {
        LOGE("Cannot read from file descriptor. Error:%d", errno);
        return 0;
    }
    return 1;
}

JNI_FUNC(jlong, PdfiumCore, nativeOpenDocument)(JNI_ARGS, jint fd, jstring password){

    size_t fileLength = (size_t)getFileSize(fd);
    if(fileLength <= 0) {
        jniThrowException(env, "java/io/IOException",
                                    "File is empty");
        return -1;
    }

    DocumentFile *docFile = new DocumentFile();

    FPDF_FILEACCESS loader;
    loader.m_FileLen = fileLength;
    loader.m_Param = reinterpret_cast<void*>(intptr_t(fd));
    loader.m_GetBlock = &getBlock;

    const char *cpassword = NULL;
    if (password != NULL) {
        cpassword = env->GetStringUTFChars(password, NULL);
    }

    FPDF_DOCUMENT document = FPDF_LoadCustomDocument(&loader, cpassword);

    if(cpassword != NULL) {
        env->ReleaseStringUTFChars(password, cpassword);
    }

    if (!document) {
        delete docFile;

        const long errorNum = FPDF_GetLastError();
        if (errorNum == FPDF_ERR_PASSWORD) {
            jniThrowException(env, "com/shockwave/pdfium/PdfPasswordException",
                                    "Password required or incorrect password.");
        }
        else {
            char* error = getErrorDescription(errorNum);
            jniThrowExceptionFmt(env, "java/io/IOException",
                                    "cannot create document: %s", error);

            free(error);
        }

        return -1;
    }

    docFile->pdfDocument = document;

    return reinterpret_cast<jlong>(docFile);
}

JNI_FUNC(jlong, PdfiumCore, nativeOpenMemDocument)(JNI_ARGS, jbyteArray data, jstring password) {
    DocumentFile *docFile = new DocumentFile();

    const char *cpassword = NULL;
    if(password != NULL) {
        cpassword = env->GetStringUTFChars(password, NULL);
    }

    jbyte *cData = env->GetByteArrayElements(data, NULL);
    int size = (int) env->GetArrayLength(data);
    jbyte *cDataCopy = new jbyte[size];
    memcpy(cDataCopy, cData, size);
    FPDF_DOCUMENT document = FPDF_LoadMemDocument( reinterpret_cast<const void*>(cDataCopy),
                                                          size, cpassword);
    env->ReleaseByteArrayElements(data, cData, JNI_ABORT);

    if(cpassword != NULL) {
        env->ReleaseStringUTFChars(password, cpassword);
    }

    if (!document) {
        delete docFile;

        const long errorNum = FPDF_GetLastError();
        if(errorNum == FPDF_ERR_PASSWORD) {
            jniThrowException(env, "com/shockwave/pdfium/PdfPasswordException",
                                    "Password required or incorrect password.");
        } else {
            char* error = getErrorDescription(errorNum);
            jniThrowExceptionFmt(env, "java/io/IOException",
                                    "cannot create document: %s", error);

            free(error);
        }

        return -1;
    }

    docFile->pdfDocument = document;

    return reinterpret_cast<jlong>(docFile);
}

JNI_FUNC(jint, PdfiumCore, nativeGetPageCount)(JNI_ARGS, jlong documentPtr){
    DocumentFile *doc = reinterpret_cast<DocumentFile*>(documentPtr);
    return (jint)FPDF_GetPageCount(doc->pdfDocument);
}

JNI_FUNC(void, PdfiumCore, nativeCloseDocument)(JNI_ARGS, jlong documentPtr){
    DocumentFile *doc = reinterpret_cast<DocumentFile*>(documentPtr);
    delete doc;
}

JNI_FUNC(jboolean, PdfiumCore, nativeSaveAsCopy)(JNI_ARGS, jlong documentPtr, jint fd){
    DocumentFile *doc = reinterpret_cast<DocumentFile*>(documentPtr);
    if (doc == NULL || doc->pdfDocument == NULL || fd < 0) {
        return false;
    }

    FdFileWrite writer;
    writer.fileWrite.version = 1;
    writer.fileWrite.WriteBlock = &writeBlockToFd;
    writer.fd = fd;
    return FPDF_SaveAsCopy(doc->pdfDocument, &writer.fileWrite, FPDF_INCREMENTAL);
}

static jlong loadPageInternal(JNIEnv *env, DocumentFile *doc, int pageIndex){
    try {
        if (doc == NULL) throw "Get page document null";

        FPDF_DOCUMENT pdfDoc = doc->pdfDocument;
        if (pdfDoc != NULL) {
            FPDF_PAGE page = FPDF_LoadPage(pdfDoc, pageIndex);
            if (page == NULL) {
                throw "Loaded page is null";
            }
            return reinterpret_cast<jlong>(page);
        }
        else {
            throw "Get page pdf document null";
        }
    }
    catch(const char *msg) {
        LOGE("%s", msg);
        jniThrowException(env, "java/lang/IllegalStateException", "cannot load page");
        return -1;
    }
}

static void closePageInternal(jlong pagePtr) {
    FPDF_ClosePage(reinterpret_cast<FPDF_PAGE>(pagePtr));
}

JNI_FUNC(jlong, PdfiumCore, nativeLoadPage)(JNI_ARGS, jlong docPtr, jint pageIndex){
    DocumentFile *doc = reinterpret_cast<DocumentFile*>(docPtr);
    return loadPageInternal(env, doc, (int)pageIndex);
}

JNI_FUNC(jlongArray, PdfiumCore, nativeLoadPages)(JNI_ARGS, jlong docPtr, jint fromIndex, jint toIndex){
    DocumentFile *doc = reinterpret_cast<DocumentFile*>(docPtr);

    if(toIndex < fromIndex) return NULL;
    jlong pages[ toIndex - fromIndex + 1 ];

    int i;
    for(i = 0; i <= (toIndex - fromIndex); i++){
        pages[i] = loadPageInternal(env, doc, (int)(i + fromIndex));
    }

    jlongArray javaPages = env -> NewLongArray( (jsize)(toIndex - fromIndex + 1) );
    env -> SetLongArrayRegion(javaPages, 0, (jsize)(toIndex - fromIndex + 1), (const jlong*)pages);

    return javaPages;
}

JNI_FUNC(void, PdfiumCore, nativeClosePage)(JNI_ARGS, jlong pagePtr) {
    closePageInternal(pagePtr);
}

JNI_FUNC(void, PdfiumCore, nativeClosePages)(JNI_ARGS, jlongArray pagesPtr) {
    int length = (int)(env -> GetArrayLength(pagesPtr));
    jlong *pages = env -> GetLongArrayElements(pagesPtr, NULL);

    int i;
    for(i = 0; i < length; i++){ closePageInternal(pages[i]); }
}

JNI_FUNC(jint, PdfiumCore, nativeGetPageWidthPixel)(JNI_ARGS, jlong pagePtr, jint dpi) {
    FPDF_PAGE page = reinterpret_cast<FPDF_PAGE>(pagePtr);
    return (jint)(FPDF_GetPageWidth(page) * dpi / 72);
}
JNI_FUNC(jint, PdfiumCore, nativeGetPageHeightPixel)(JNI_ARGS, jlong pagePtr, jint dpi) {
    FPDF_PAGE page = reinterpret_cast<FPDF_PAGE>(pagePtr);
    return (jint)(FPDF_GetPageHeight(page) * dpi / 72);
}

JNI_FUNC(jint, PdfiumCore, nativeGetPageWidthPoint)(JNI_ARGS, jlong pagePtr){
    FPDF_PAGE page = reinterpret_cast<FPDF_PAGE>(pagePtr);
    return (jint)FPDF_GetPageWidth(page);
}
JNI_FUNC(jint, PdfiumCore, nativeGetPageHeightPoint)(JNI_ARGS, jlong pagePtr){
    FPDF_PAGE page = reinterpret_cast<FPDF_PAGE>(pagePtr);
    return (jint)FPDF_GetPageHeight(page);
}

JNI_FUNC(jobject, PdfiumCore, nativeGetPageSizeByIndex)(JNI_ARGS, jlong docPtr, jint pageIndex, jint dpi){
    DocumentFile *doc = reinterpret_cast<DocumentFile*>(docPtr);
    if(doc == NULL) {
        LOGE("Document is null");

        jniThrowException(env, "java/lang/IllegalStateException",
                               "Document is null");
        return NULL;
    }

    double width, height;
    int result = FPDF_GetPageSizeByIndex(doc->pdfDocument, pageIndex, &width, &height);
    //LOGD("width : %.2f", width);
    //LOGD("height: %.2f", height);

    if (result == 0) {
        width = 0;
        height = 0;
    }

    jint widthInt = (jint) (width * dpi / 72);
    jint heightInt = (jint) (height * dpi / 72);

    jclass clazz = env->FindClass("com/shockwave/pdfium/util/Size");
    jmethodID constructorID = env->GetMethodID(clazz, "<init>", "(II)V");
    return env->NewObject(clazz, constructorID, widthInt, heightInt);
}

JNI_FUNC(jobject, PdfiumCore, nativeGetPageSizePointByIndex)(JNI_ARGS, jlong docPtr, jint pageIndex){
    DocumentFile *doc = reinterpret_cast<DocumentFile*>(docPtr);
    if(doc == NULL) {
        LOGE("Document is null");

        jniThrowException(env, "java/lang/IllegalStateException",
                               "Document is null");
        return NULL;
    }

    double width, height;
    int result = FPDF_GetPageSizeByIndex(doc->pdfDocument, pageIndex, &width, &height);
    if (result == 0) {
        width = 0;
        height = 0;
    }

    jclass clazz = env->FindClass("com/shockwave/pdfium/util/SizeF");
    jmethodID constructorID = env->GetMethodID(clazz, "<init>", "(FF)V");
    return env->NewObject(clazz, constructorID, static_cast<jfloat>(width), static_cast<jfloat>(height));
}

static void renderPageInternal(
    FPDF_PAGE page,
    ANativeWindow_Buffer *windowBuffer,
    int startX, int startY,
    int canvasHorSize, int canvasVerSize,
    int drawSizeHor, int drawSizeVer,
    bool renderAnnot
) {

    FPDF_BITMAP pdfBitmap = FPDFBitmap_CreateEx(
        canvasHorSize,
        canvasVerSize,
        FPDFBitmap_BGRA,
        windowBuffer->bits,
        (int)(windowBuffer->stride) * 4
    );

    /*LOGD("Start X: %d", startX);
    LOGD("Start Y: %d", startY);
    LOGD("Canvas Hor: %d", canvasHorSize);
    LOGD("Canvas Ver: %d", canvasVerSize);
    LOGD("Draw Hor: %d", drawSizeHor);
    LOGD("Draw Ver: %d", drawSizeVer);*/

    if(drawSizeHor < canvasHorSize || drawSizeVer < canvasVerSize){
        FPDFBitmap_FillRect( pdfBitmap, 0, 0, canvasHorSize, canvasVerSize,
                             0x848484FF); //Gray
    }

    int baseHorSize = (canvasHorSize < drawSizeHor)? canvasHorSize : drawSizeHor;
    int baseVerSize = (canvasVerSize < drawSizeVer)? canvasVerSize : drawSizeVer;
    int baseX = (startX < 0)? 0 : startX;
    int baseY = (startY < 0)? 0 : startY;
    int flags = FPDF_REVERSE_BYTE_ORDER;

    if(renderAnnot) {
    	flags |= FPDF_ANNOT;
    }

    FPDFBitmap_FillRect( pdfBitmap, baseX, baseY, baseHorSize, baseVerSize,
                         0xFFFFFFFF); //White

    FPDF_RenderPageBitmap( pdfBitmap, page,
                           startX, startY,
                           drawSizeHor, drawSizeVer,
                           0, flags );

    // TODO: You need to use FPDF_FFLDraw after FPDF_RenderPageBitmap to render form annotations
}

JNI_FUNC(void, PdfiumCore, nativeRenderPage)(JNI_ARGS, jlong pagePtr, jobject objSurface,
                                             jint dpi, jint startX, jint startY,
                                             jint drawSizeHor, jint drawSizeVer,
                                             jboolean renderAnnot){
    ANativeWindow *nativeWindow = ANativeWindow_fromSurface(env, objSurface);
    if(nativeWindow == NULL){
        LOGE("native window pointer null");
        return;
    }
    FPDF_PAGE page = reinterpret_cast<FPDF_PAGE>(pagePtr);

    if(page == NULL || nativeWindow == NULL){
        LOGE("Render page pointers invalid");
        return;
    }

    if(ANativeWindow_getFormat(nativeWindow) != WINDOW_FORMAT_RGBA_8888){
        LOGD("Set format to RGBA_8888");
        ANativeWindow_setBuffersGeometry( nativeWindow,
                                          ANativeWindow_getWidth(nativeWindow),
                                          ANativeWindow_getHeight(nativeWindow),
                                          WINDOW_FORMAT_RGBA_8888 );
    }

    ANativeWindow_Buffer buffer;
    int ret;
    if( (ret = ANativeWindow_lock(nativeWindow, &buffer, NULL)) != 0 ){
        LOGE("Locking native window failed: %s", strerror(ret * -1));
        return;
    }

    renderPageInternal(page, &buffer,
                       (int)startX, (int)startY,
                       buffer.width, buffer.height,
                       (int)drawSizeHor, (int)drawSizeVer,
                       (bool)renderAnnot);

    ANativeWindow_unlockAndPost(nativeWindow);
    ANativeWindow_release(nativeWindow);
}

// Added but not tested at all.
static FS_RECTF calculateContentBounds(FPDF_PAGE page) {
    float min_x = 10000, min_y = 10000, max_x = 0, max_y = 0;
    int objCount = FPDFPage_CountObjects(page);

    for (int i = 0; i < objCount; ++i) {
        FPDF_PAGEOBJECT pageObject = FPDFPage_GetObject(page, i);
        float left, bottom, right, top;
        if (FPDFPageObj_GetBounds(pageObject, &left, &bottom, &right, &top)) {
            min_x = std::min(min_x, left);
            min_y = std::min(min_y, bottom);
            max_x = std::max(max_x, right);
            max_y = std::max(max_y, top);
        }
    }

    FS_RECTF bounds = {min_x, min_y, max_x, max_y};
    return bounds;
}

/*
 * Breakdown of nativeRenderPageBitmap
 * 1. Validity Checks: Initially, the function checks for null pointers in the page and bitmap
 *    objects, which is a standard safety measure.
 *
 * 2. Bitmap Info Retrieval: It retrieves bitmap properties like width, height, and format.
 *    The function expects the bitmap format to be either RGBA_8888 or RGB_565, which are
 *    common for image handling in Android.
 *
 * 3. Locking Bitmap Pixels: Before drawing into the bitmap, it locks the pixels to prevent
 *    other processes from modifying them simultaneously.
 *
 * 4. Buffer Setup: Depending on the bitmap format, it prepares the buffer that will be used for drawing:
 *     - RGBA_8888: Directly uses the locked bitmap memory.
 *     - RGB_565  : Allocates a temporary buffer to be used for the operation, requiring a
 *                  conversion later on.
 *
 * 5. PDF Bitmap Creation: Creates an FPDF_BITMAP using FPDFBitmap_CreateEx, which is
 * directly tied to the buffer prepared earlier.
 *
 * 6. Drawing Background: Fills the entire bitmap area with a gray color if the specified
 *    drawing size is smaller than the canvas size, providing a default background.
 *
 * 7. Rendering the PDF Page: Renders the page into the bitmap using FPDF_RenderPageBitmap.
 *    If annotations are enabled, it also renders annotations with FPDF_ANNOT.
 *
 * 8. Form Handling: Initializes form handling and renders form fields onto the bitmap,
 *    which is crucial for PDFs that contain forms.
 *
 * 9. Handling RGB_565 Format: If the format was RGB_565, it converts the temporary buffer
 *    to RGB_565 format and copies it back to the bitmap.
 *
 * 10. Cleaning Up: Finally, it unlocks the bitmap pixels and cleans up resources like
 *     the temporary buffer if it was used.
 * */
JNI_FUNC(void, PdfiumCore, nativeRenderPageBitmap)(JNI_ARGS, jlong docPtr, jlong pagePtr, jobject bitmap,
                                             jint dpi, jint startX, jint startY,
                                             jint drawSizeHor, jint drawSizeVer,
                                             jboolean renderAnnot){

    FPDF_PAGE page = reinterpret_cast<FPDF_PAGE>(pagePtr);

    if(page == NULL || bitmap == NULL){
        LOGE("Render page pointers invalid");
        return;
    }

    AndroidBitmapInfo info;
    int ret;
    if((ret = AndroidBitmap_getInfo(env, bitmap, &info)) < 0) {
        LOGE("Fetching bitmap info failed: %s", strerror(ret * -1));
        return;
    }

    int canvasHorSize = info.width;
    int canvasVerSize = info.height;

    if(info.format != ANDROID_BITMAP_FORMAT_RGBA_8888 && info.format != ANDROID_BITMAP_FORMAT_RGB_565){
        LOGE("Bitmap format must be RGBA_8888 or RGB_565");
        return;
    }

    void *addr;
    if( (ret = AndroidBitmap_lockPixels(env, bitmap, &addr)) != 0 ){
        LOGE("Locking bitmap failed: %s", strerror(ret * -1));
        return;
    }

    void *tmp;
    int format;
    int sourceStride;
    if (info.format == ANDROID_BITMAP_FORMAT_RGB_565) {
        tmp = malloc(canvasVerSize * canvasHorSize * sizeof(rgb));
        sourceStride = canvasHorSize * sizeof(rgb);
        format = FPDFBitmap_BGR;
    } else {
        tmp = addr;
        sourceStride = info.stride;
        format = FPDFBitmap_BGRA;
    }

    FPDF_BITMAP pdfBitmap = FPDFBitmap_CreateEx( canvasHorSize, canvasVerSize,
                                                     format, tmp, sourceStride);

    /*LOGD("Start X: %d", startX);
    LOGD("Start Y: %d", startY);
    LOGD("Canvas Hor: %d", canvasHorSize);
    LOGD("Canvas Ver: %d", canvasVerSize);
    LOGD("Draw Hor: %d", drawSizeHor);
    LOGD("Draw Ver: %d", drawSizeVer);*/

    if(drawSizeHor < canvasHorSize || drawSizeVer < canvasVerSize){
        FPDFBitmap_FillRect( pdfBitmap, 0, 0, canvasHorSize, canvasVerSize,
                             0x848484FF); //Gray
    }

    int baseHorSize = (canvasHorSize < drawSizeHor)? canvasHorSize : (int)drawSizeHor;
    int baseVerSize = (canvasVerSize < drawSizeVer)? canvasVerSize : (int)drawSizeVer;
    int baseX = (startX < 0)? 0 : (int)startX;
    int baseY = (startY < 0)? 0 : (int)startY;
    int flags = FPDF_REVERSE_BYTE_ORDER;

    if(renderAnnot) {
    	flags |= FPDF_ANNOT;
    }

    FPDFBitmap_FillRect( pdfBitmap, baseX, baseY, baseHorSize, baseVerSize,
                         0xFFFFFFFF); //White

    FPDF_RenderPageBitmap(
        pdfBitmap, page, startX, startY, (int)drawSizeHor, (int)drawSizeVer, 0, flags
    );

    DocumentFile *doc = reinterpret_cast<DocumentFile*>(docPtr);

    FPDF_FORMFILLINFO formfillinfo;
    formfillinfo.version = 1;

    FPDF_FORMHANDLE formHandle = FPDFDOC_InitFormFillEnvironment(doc->pdfDocument, &formfillinfo);

    FPDF_FFLDraw(
        formHandle, pdfBitmap, page, startX, startY, (int)drawSizeHor, (int)drawSizeVer, 0, flags
    );

    if (info.format == ANDROID_BITMAP_FORMAT_RGB_565) {
        rgbBitmapTo565(tmp, sourceStride, addr, &info);
        free(tmp);
    }

    AndroidBitmap_unlockPixels(env, bitmap);
}

int mapToDisplay(int dpi, int x) {
    return x * dpi / 72;
    //return x * dpi / 72;
}

int mapToOriginal(int dpi, int y) {
    return y;
    //return 72 * y / dpi;
}

JNI_FUNC(jboolean, PdfiumCore, nativeCreateAnnotInPage)(JNI_ARGS,
    jlong pagePtr,
    jint left,
    jint right,
    jint top,
    jint bottom,
    jint dpi,
    jboolean hasPadding
) {
    FPDF_PAGE page = reinterpret_cast<FPDF_PAGE>(pagePtr);

    if (FPDFAnnot_IsObjectSupportedSubtype(FPDF_ANNOT_HIGHLIGHT)) return false;
    FPDF_ANNOTATION annot = FPDFPage_CreateAnnot(page, FPDF_ANNOT_HIGHLIGHT);

    int annotIndex = FPDFPage_GetAnnotIndex(page, annot);
    searchResultAnnotIndexes.push_back(annotIndex);

    int red = 255, green = 0, blue = 0, alpha = 255;
    //FPDFAnnot_SetColor(annot, FPDFANNOT_COLORTYPE_InteriorColor, red, green, blue, alpha);
    FPDFAnnot_SetColor(annot, FPDFANNOT_COLORTYPE_Color, red, green, blue, alpha);

    int padding = hasPadding ? 2 : 0;

    FS_RECTF rectF;
    rectF.left = left - padding;
    rectF.top = top + padding;
    rectF.right = right + padding;
    rectF.bottom = bottom - (padding + 1);   // increase it for bottom
    FPDFAnnot_SetRect(annot, &rectF);

    /*
       // FS_QUADPOINTSF
       leftTop 		p1
       rightTop	 	p2
       leftBottom	p3
       rightBottom	p4
    */
    FS_QUADPOINTSF quadpoints;

    quadpoints.x1 = rectF.left;
    quadpoints.y1 = rectF.top;

    quadpoints.x2 = rectF.right;
    quadpoints.y2 = rectF.top;

    quadpoints.x3 = rectF.left;
    quadpoints.y3 = rectF.bottom;

    quadpoints.x4 = rectF.right;
    quadpoints.y4 = rectF.bottom;

    FPDFAnnot_AppendAttachmentPoints(annot, & quadpoints);
    FPDFPage_CloseAnnot(annot);

    return true;
}

JNI_FUNC(jboolean, PdfiumCore, nativeCreateHighlightAnnotation)(JNI_ARGS,
    jlong pagePtr,
    jobjectArray rects,
    jint red,
    jint green,
    jint blue,
    jint alpha,
    jstring contents
) {
    FPDF_PAGE page = reinterpret_cast<FPDF_PAGE>(pagePtr);
    (void) alpha;
    if (page == NULL || rects == NULL) {
        return false;
    }
    if (!FPDFAnnot_IsSupportedSubtype(FPDF_ANNOT_HIGHLIGHT)) {
        return false;
    }

    jsize rectCount = env->GetArrayLength(rects);
    if (rectCount <= 0) {
        return false;
    }

    bool success = true;
    bool createdAny = false;
    std::vector<int> createdIndexes;
    std::string groupName = makeAnnotName();

    for (jsize i = 0; i < rectCount; i++) {
        jfloatArray rectArray = static_cast<jfloatArray>(env->GetObjectArrayElement(rects, i));
        if (rectArray == NULL) {
            success = false;
            break;
        }
        if (env->GetArrayLength(rectArray) < 4) {
            env->DeleteLocalRef(rectArray);
            success = false;
            break;
        }

        jfloat values[4];
        env->GetFloatArrayRegion(rectArray, 0, 4, values);
        env->DeleteLocalRef(rectArray);
        if (env->ExceptionCheck()) {
            success = false;
            break;
        }

        FS_RECTF rect;
        rect.left = fmin(values[0], values[2]);
        rect.right = fmax(values[0], values[2]);
        rect.top = fmax(values[1], values[3]);
        rect.bottom = fmin(values[1], values[3]);
        if (rect.right <= rect.left || rect.top <= rect.bottom) {
            continue;
        }
        float verticalPadding = fmax(0.5f, (rect.top - rect.bottom) * 0.08f);
        rect.top += verticalPadding;
        rect.bottom -= verticalPadding;

        FPDF_ANNOTATION annot = FPDFPage_CreateAnnot(page, FPDF_ANNOT_HIGHLIGHT);
        if (annot == NULL) {
            success = false;
            break;
        }

        FS_QUADPOINTSF quadpoints;
        quadpoints.x1 = rect.left;
        quadpoints.y1 = rect.top;
        quadpoints.x2 = rect.right;
        quadpoints.y2 = rect.top;
        quadpoints.x3 = rect.left;
        quadpoints.y3 = rect.bottom;
        quadpoints.x4 = rect.right;
        quadpoints.y4 = rect.bottom;
        std::string name = makeAnnotName();
        std::string colorValue = colorToHex(red, green, blue);
        bool annotSuccess = FPDFAnnot_SetRect(annot, &rect)
                && FPDFAnnot_SetColor(annot, FPDFANNOT_COLORTYPE_Color, red, green, blue, 255)
                && FPDFAnnot_AppendAttachmentPoints(annot, &quadpoints)
                && FPDFAnnot_SetFlags(annot, FPDF_ANNOT_FLAG_PRINT)
                && setAnnotWideString(env, annot, "Contents", contents)
                && setAnnotAsciiString(annot, "T", "MJ PDF")
                && setAnnotAsciiString(annot, "NM", name.c_str())
                && setAnnotAsciiString(annot, "MJGroup", groupName.c_str())
                && setAnnotAsciiString(annot, "MJColor", colorValue.c_str());
        int annotIndex = FPDFPage_GetAnnotIndex(page, annot);
        FPDFPage_CloseAnnot(annot);
        if (!annotSuccess) {
            if (annotIndex >= 0) {
                FPDFPage_RemoveAnnot(page, annotIndex);
            }
            success = false;
            break;
        }
        if (annotIndex >= 0) {
            createdIndexes.push_back(annotIndex);
        }
        createdAny = true;
    }

    if (!createdAny) {
        success = false;
    }
    if (!success) {
        for (std::vector<int>::reverse_iterator it = createdIndexes.rbegin(); it != createdIndexes.rend(); ++it) {
            FPDFPage_RemoveAnnot(page, *it);
        }
    }

    return success;
}

JNI_FUNC(jobject, PdfiumCore, nativeGetHighlightAnnotationAt)(JNI_ARGS,
    jlong pagePtr,
    jfloat x,
    jfloat y,
    jfloat tolerance
) {
    FPDF_PAGE page = reinterpret_cast<FPDF_PAGE>(pagePtr);
    if (page == NULL) {
        return NULL;
    }

    int annotCount = FPDFPage_GetAnnotCount(page);
    int hitIndex = -1;
    FS_RECTF hitRect;
    std::vector<FPDF_WCHAR> hitGroup;
    std::vector<FPDF_WCHAR> hitContents;

    for (int i = annotCount - 1; i >= 0; i--) {
        if (isSearchResultAnnotIndex(i)) {
            continue;
        }
        FPDF_ANNOTATION annot = FPDFPage_GetAnnot(page, i);
        if (annot == NULL) {
            continue;
        }

        FS_RECTF rect;
        bool matches = isAppHighlightAnnotation(annot)
                && FPDFAnnot_GetRect(annot, &rect)
                && rectContainsPoint(rect, x, y, tolerance);
        if (matches) {
            hitIndex = i;
            hitRect = normalizedRect(rect);
            hitGroup = getAnnotWideString(annot, "MJGroup");
            hitContents = getAnnotWideString(annot, "Contents");
        }
        FPDFPage_CloseAnnot(annot);
        if (matches) {
            break;
        }
    }

    if (hitIndex < 0) {
        return NULL;
    }

    FS_RECTF bounds = hitRect;
    if (!hitGroup.empty()) {
        bool foundGroupRect = false;
        for (int i = 0; i < annotCount; i++) {
            FPDF_ANNOTATION annot = FPDFPage_GetAnnot(page, i);
            if (annot == NULL) {
                continue;
            }
            FS_RECTF rect;
            bool sameGroup = isAppHighlightAnnotation(annot)
                    && wideEquals(getAnnotWideString(annot, "MJGroup"), hitGroup)
                    && FPDFAnnot_GetRect(annot, &rect);
            FPDFPage_CloseAnnot(annot);
            if (!sameGroup) {
                continue;
            }
            if (!foundGroupRect) {
                bounds = normalizedRect(rect);
                foundGroupRect = true;
            } else {
                unionRect(&bounds, rect);
            }
        }
    }

    jclass rectClass = env->FindClass("android/graphics/RectF");
    if (rectClass == NULL) {
        return NULL;
    }
    jmethodID rectConstructor = env->GetMethodID(rectClass, "<init>", "(FFFF)V");
    if (rectConstructor == NULL) {
        env->DeleteLocalRef(rectClass);
        return NULL;
    }
    jobject rectObject = env->NewObject(rectClass, rectConstructor,
            static_cast<jfloat>(bounds.left),
            static_cast<jfloat>(bounds.top),
            static_cast<jfloat>(bounds.right),
            static_cast<jfloat>(bounds.bottom));
    env->DeleteLocalRef(rectClass);
    if (rectObject == NULL) {
        return NULL;
    }

    jclass hitClass = env->FindClass("com/shockwave/pdfium/PdfDocument$HighlightAnnotation");
    if (hitClass == NULL) {
        env->DeleteLocalRef(rectObject);
        return NULL;
    }
    jmethodID hitConstructor = env->GetMethodID(hitClass, "<init>",
            "(ILjava/lang/String;Landroid/graphics/RectF;Ljava/lang/String;)V");
    if (hitConstructor == NULL) {
        env->DeleteLocalRef(hitClass);
        env->DeleteLocalRef(rectObject);
        return NULL;
    }

    jstring groupString = wideToJString(env, hitGroup);
    jstring contentsString = wideToJString(env, hitContents);
    jobject hitObject = env->NewObject(hitClass, hitConstructor,
            static_cast<jint>(hitIndex), groupString, rectObject, contentsString);
    env->DeleteLocalRef(hitClass);
    env->DeleteLocalRef(rectObject);
    if (groupString != NULL) {
        env->DeleteLocalRef(groupString);
    }
    if (contentsString != NULL) {
        env->DeleteLocalRef(contentsString);
    }
    return hitObject;
}

JNI_FUNC(jobjectArray, PdfiumCore, nativeGetHighlightAnnotations)(JNI_ARGS,
    jlong pagePtr
) {
    FPDF_PAGE page = reinterpret_cast<FPDF_PAGE>(pagePtr);
    jclass hitClass = env->FindClass("com/shockwave/pdfium/PdfDocument$HighlightAnnotation");
    if (hitClass == NULL) {
        return NULL;
    }
    if (page == NULL) {
        return env->NewObjectArray(0, hitClass, NULL);
    }

    jclass rectClass = env->FindClass("android/graphics/RectF");
    if (rectClass == NULL) {
        env->DeleteLocalRef(hitClass);
        return NULL;
    }
    jmethodID rectConstructor = env->GetMethodID(rectClass, "<init>", "(FFFF)V");
    jmethodID hitConstructor = env->GetMethodID(hitClass, "<init>",
            "(ILjava/lang/String;Landroid/graphics/RectF;Ljava/lang/String;I)V");
    if (rectConstructor == NULL || hitConstructor == NULL) {
        env->DeleteLocalRef(rectClass);
        env->DeleteLocalRef(hitClass);
        return NULL;
    }

    std::vector<jobject> annotations;
    int annotCount = FPDFPage_GetAnnotCount(page);
    for (int i = 0; i < annotCount; i++) {
        FPDF_ANNOTATION annot = FPDFPage_GetAnnot(page, i);
        if (annot == NULL) {
            continue;
        }
        if (FPDFAnnot_GetSubtype(annot) != FPDF_ANNOT_HIGHLIGHT) {
            FPDFPage_CloseAnnot(annot);
            continue;
        }

        FS_RECTF rect;
        if (!FPDFAnnot_GetRect(annot, &rect)) {
            FPDFPage_CloseAnnot(annot);
            continue;
        }
        FS_RECTF bounds = normalizedRect(rect);
        jobject rectObject = env->NewObject(rectClass, rectConstructor,
                static_cast<jfloat>(bounds.left),
                static_cast<jfloat>(bounds.top),
                static_cast<jfloat>(bounds.right),
                static_cast<jfloat>(bounds.bottom));
        if (rectObject == NULL) {
            FPDFPage_CloseAnnot(annot);
            continue;
        }

        unsigned int red = 255;
        unsigned int green = 255;
        unsigned int blue = 0;
        unsigned int alpha = 255;
        getHighlightAnnotationColor(annot, &red, &green, &blue, &alpha);
        int color = 0xFF000000
                | ((red & 0xFF) << 16)
                | ((green & 0xFF) << 8)
                | (blue & 0xFF);
        jstring groupString = wideToJString(env, getAnnotWideString(annot, "MJGroup"));
        jstring contentsString = wideToJString(env, getAnnotWideString(annot, "Contents"));
        jobject hitObject = env->NewObject(hitClass, hitConstructor,
                static_cast<jint>(i), groupString, rectObject, contentsString, static_cast<jint>(color));
        if (hitObject != NULL) {
            annotations.push_back(env->NewGlobalRef(hitObject));
            env->DeleteLocalRef(hitObject);
        }
        if (groupString != NULL) {
            env->DeleteLocalRef(groupString);
        }
        if (contentsString != NULL) {
            env->DeleteLocalRef(contentsString);
        }
        env->DeleteLocalRef(rectObject);
        FPDFPage_CloseAnnot(annot);
    }

    jobjectArray result = env->NewObjectArray(annotations.size(), hitClass, NULL);
    for (jsize i = 0; i < static_cast<jsize>(annotations.size()); i++) {
        env->SetObjectArrayElement(result, i, annotations[i]);
        env->DeleteGlobalRef(annotations[i]);
    }
    env->DeleteLocalRef(rectClass);
    env->DeleteLocalRef(hitClass);
    return result;
}

JNI_FUNC(jboolean, PdfiumCore, nativeSetHighlightAnnotationColor)(JNI_ARGS,
    jlong pagePtr,
    jint annotIndex,
    jstring groupKey,
    jint red,
    jint green,
    jint blue
) {
    FPDF_PAGE page = reinterpret_cast<FPDF_PAGE>(pagePtr);
    if (page == NULL) {
        return false;
    }

    int annotCount = FPDFPage_GetAnnotCount(page);
    std::vector<FPDF_WCHAR> group = jstringToWide(env, groupKey);
    bool updatedAny = false;
    bool success = true;
    std::string colorValue = colorToHex(red, green, blue);

    for (int i = 0; i < annotCount; i++) {
        if (group.empty() && i != annotIndex) {
            continue;
        }
        FPDF_ANNOTATION annot = FPDFPage_GetAnnot(page, i);
        if (annot == NULL) {
            continue;
        }

        bool matches = isAppHighlightAnnotation(annot)
                && (group.empty() || wideEquals(getAnnotWideString(annot, "MJGroup"), group));
        if (matches) {
            updatedAny = true;
            FPDFAnnot_SetAP(annot, FPDF_ANNOT_APPEARANCEMODE_NORMAL, NULL);
            if (!FPDFAnnot_SetColor(annot, FPDFANNOT_COLORTYPE_Color, red, green, blue, 255)
                    || !setAnnotAsciiString(annot, "MJColor", colorValue.c_str())) {
                success = false;
            }
            FPDFAnnot_SetFlags(annot, FPDF_ANNOT_FLAG_PRINT);
        }
        FPDFPage_CloseAnnot(annot);

        if (group.empty() && i == annotIndex) {
            break;
        }
    }

    return updatedAny && success;
}

JNI_FUNC(jboolean, PdfiumCore, nativeRemoveHighlightAnnotation)(JNI_ARGS,
    jlong pagePtr,
    jint annotIndex,
    jstring groupKey
) {
    FPDF_PAGE page = reinterpret_cast<FPDF_PAGE>(pagePtr);
    if (page == NULL) {
        return false;
    }

    int annotCount = FPDFPage_GetAnnotCount(page);
    std::vector<FPDF_WCHAR> group = jstringToWide(env, groupKey);
    bool removedAny = false;
    bool success = true;

    for (int i = annotCount - 1; i >= 0; i--) {
        if (group.empty() && i != annotIndex) {
            continue;
        }
        FPDF_ANNOTATION annot = FPDFPage_GetAnnot(page, i);
        if (annot == NULL) {
            continue;
        }
        bool matches = isAppHighlightAnnotation(annot)
                && (group.empty() || wideEquals(getAnnotWideString(annot, "MJGroup"), group));
        FPDFPage_CloseAnnot(annot);
        if (matches) {
            removedAny = true;
            if (!FPDFPage_RemoveAnnot(page, i)) {
                success = false;
            }
        }

        if (group.empty() && i == annotIndex) {
            break;
        }
    }

    return removedAny && success;
}

JNI_FUNC(jint, PdfiumCore, nativeClearSearchResultAnnot)(JNI_ARGS, jlong pagePtr) {
    // TODO: this should be converted to a map that has both pageIndexes and a vector of results annot indexes
    FPDF_PAGE page = reinterpret_cast<FPDF_PAGE>(pagePtr);
    while (!searchResultAnnotIndexes.empty()) {
        int index = searchResultAnnotIndexes.back();
        FPDFPage_RemoveAnnot(page, index);
        searchResultAnnotIndexes.pop_back();
    }

    return searchResultAnnotIndexes.size();
}

JNI_FUNC(jstring, PdfiumCore, nativeGetPageText)(JNI_ARGS, jlong pagePtr) {
    FPDF_PAGE page = reinterpret_cast<FPDF_PAGE>(pagePtr);
    FPDF_TEXTPAGE pageText = FPDFText_LoadPage(page);
    if (pageText == NULL) {
        return env->NewStringUTF("");
    }

    auto charCount = FPDFText_CountChars(pageText);
    if (charCount <= 0) {
        FPDFText_ClosePage(pageText);
        return env->NewStringUTF("");
    }
    jchar* str = (jchar*) malloc((charCount + 1) * sizeof(jchar));
    if (str == NULL) {
        FPDFText_ClosePage(pageText);
        return env->NewStringUTF("");
    }
    int length = FPDFText_GetText(pageText, 0, charCount, str);

    // ---------
    //LOGD("string: %s", env->GetStringUTFChars(string, 0));
    //for (int i = 0; i < length; ++i) {
    //    if (str[i] == -2) str[i] = '-';
    //    //LOGD("char: %c value: %hd", str[i], str[i]);
    //}
    // ---------

    if (length > 0) {
        jstring result = env->NewString(str, length - 1);       // no trailing zero
        free(str);
        FPDFText_ClosePage(pageText);
        return result;
    }
    else {
        free(str);
        FPDFText_ClosePage(pageText);
        return env->NewStringUTF("");
    }
}

JNI_FUNC(jobjectArray, PdfiumCore, nativeGetPageTextBounds)(JNI_ARGS, jlong pagePtr, jint start, jint count) {
    FPDF_PAGE page = reinterpret_cast<FPDF_PAGE>(pagePtr);
    jclass rectCls = env->FindClass("android/graphics/Rect");
    FPDF_TEXTPAGE pageText = FPDFText_LoadPage(page);
    if (pageText == NULL) {
        return env->NewObjectArray(0, rectCls, 0);
    }

    int rectsCount = FPDFText_CountRects(pageText, start, count);
    jmethodID constructorID = env->GetMethodID(rectCls, "<init>", "(IIII)V");
    jobjectArray array = env->NewObjectArray(rectsCount, rectCls, 0);

    for (int i = 0; i < rectsCount; ++i) {
        double left = -1, top = -1, right = -1, bottom = -1;
        FPDFText_GetRect(pageText, i, &left, &top, &right, &bottom);
        jobject sizeObj = env->NewObject(
            rectCls, constructorID,
            (int) floor(left), (int) ceil(top), (int) floor(right), (int) ceil(bottom)
        );
        env->SetObjectArrayElement(array, i, sizeObj);
        env->DeleteLocalRef(sizeObj);
    }

    FPDFText_ClosePage(pageText);
    return array;
}

JNI_FUNC(jstring, PdfiumCore, nativeGetPagesText)(JNI_ARGS, jlong pagePtr, jlong start, jlong end) {
    FPDF_PAGE page = reinterpret_cast<FPDF_PAGE>(pagePtr);
    FPDF_TEXTPAGE text = FPDFText_LoadPage(page);
    if (text == NULL) {
        return env->NewStringUTF("");
    }

    auto length = FPDFText_CountChars(text);
    if (length <= 0) {
        FPDFText_ClosePage(text);
        return env->NewStringUTF("");
    }
    jchar *str = (jchar *) malloc((length + 1) * sizeof(jchar));
    if (str == NULL) {
        FPDFText_ClosePage(text);
        return env->NewStringUTF("");
    }
    int len = FPDFText_GetText(text, 0, length, str);
    if (len > 0) {
        jstring s = env->NewString(str, len - 1);   // no trailing zero
        free(str);
        FPDFText_ClosePage(text);
        return s;
    } else {
        free(str);
        FPDFText_ClosePage(text);
        return env->NewStringUTF("");
    }
}

JNI_FUNC(jlong, PdfiumCore, nativeLoadTextPage)(JNI_ARGS, jlong pagePtr) {
    FPDF_PAGE page = reinterpret_cast<FPDF_PAGE>(pagePtr);
    if (page == NULL) {
        return 0;
    }

    FPDF_TEXTPAGE textPage = FPDFText_LoadPage(page);
    return reinterpret_cast<jlong>(textPage);
}

JNI_FUNC(void, PdfiumCore, nativeCloseTextPage)(JNI_ARGS, jlong textPagePtr) {
    FPDF_TEXTPAGE textPage = reinterpret_cast<FPDF_TEXTPAGE>(textPagePtr);
    if (textPage != NULL) {
        FPDFText_ClosePage(textPage);
    }
}

JNI_FUNC(jint, PdfiumCore, nativeTextCountChars)(JNI_ARGS, jlong textPagePtr) {
    FPDF_TEXTPAGE textPage = reinterpret_cast<FPDF_TEXTPAGE>(textPagePtr);
    if (textPage == NULL) {
        return -1;
    }
    return FPDFText_CountChars(textPage);
}

JNI_FUNC(jint, PdfiumCore, nativeCharIndexAtPos)(JNI_ARGS, jlong textPagePtr,
    jdouble x,
    jdouble y,
    jdouble xTolerance,
    jdouble yTolerance
) {
    FPDF_TEXTPAGE textPage = reinterpret_cast<FPDF_TEXTPAGE>(textPagePtr);
    if (textPage == NULL) {
        return -1;
    }
    return FPDFText_GetCharIndexAtPos(textPage, x, y, xTolerance, yTolerance);
}

JNI_FUNC(jboolean, PdfiumCore, nativeLooseCharBox)(JNI_ARGS, jlong textPagePtr,
    jint index,
    jfloatArray out4
) {
    FPDF_TEXTPAGE textPage = reinterpret_cast<FPDF_TEXTPAGE>(textPagePtr);
    if (textPage == NULL || out4 == NULL || env->GetArrayLength(out4) < 4) {
        return false;
    }

    FS_RECTF rect;
    if (!FPDFText_GetLooseCharBox(textPage, index, &rect)) {
        return false;
    }

    jfloat values[4] = {
        static_cast<jfloat>(rect.left),
        static_cast<jfloat>(rect.bottom),
        static_cast<jfloat>(rect.right),
        static_cast<jfloat>(rect.top)
    };
    env->SetFloatArrayRegion(out4, 0, 4, values);
    return true;
}

JNI_FUNC(jboolean, PdfiumCore, nativeTightCharBox)(JNI_ARGS, jlong textPagePtr,
    jint index,
    jfloatArray out4
) {
    FPDF_TEXTPAGE textPage = reinterpret_cast<FPDF_TEXTPAGE>(textPagePtr);
    if (textPage == NULL || out4 == NULL || env->GetArrayLength(out4) < 4) {
        return false;
    }

    double left = 0, right = 0, bottom = 0, top = 0;
    if (!FPDFText_GetCharBox(textPage, index, &left, &right, &bottom, &top)) {
        return false;
    }

    jfloat values[4] = {
        static_cast<jfloat>(left),
        static_cast<jfloat>(bottom),
        static_cast<jfloat>(right),
        static_cast<jfloat>(top)
    };
    env->SetFloatArrayRegion(out4, 0, 4, values);
    return true;
}

JNI_FUNC(jint, PdfiumCore, nativeCharUnicode)(JNI_ARGS, jlong textPagePtr, jint index) {
    FPDF_TEXTPAGE textPage = reinterpret_cast<FPDF_TEXTPAGE>(textPagePtr);
    if (textPage == NULL) {
        return 0;
    }
    return static_cast<jint>(FPDFText_GetUnicode(textPage, index));
}

JNI_FUNC(jstring, PdfiumCore, nativeTextRange)(JNI_ARGS, jlong textPagePtr,
    jint start,
    jint count
) {
    FPDF_TEXTPAGE textPage = reinterpret_cast<FPDF_TEXTPAGE>(textPagePtr);
    if (textPage == NULL || count <= 0) {
        return env->NewStringUTF("");
    }

    int charCount = FPDFText_CountChars(textPage);
    if (charCount <= 0) {
        return env->NewStringUTF("");
    }
    if (start < 0) {
        start = 0;
    }
    if (start >= charCount) {
        return env->NewStringUTF("");
    }

    int available = charCount - start;
    int safeCount = count < available ? count : available;
    if (safeCount <= 0) {
        return env->NewStringUTF("");
    }

    jchar *str = (jchar *) malloc((safeCount + 1) * sizeof(jchar));
    if (str == NULL) {
        return env->NewStringUTF("");
    }

    int len = FPDFText_GetText(textPage, start, safeCount, str);
    if (len <= 0) {
        free(str);
        return env->NewStringUTF("");
    }

    jstring result = env->NewString(str, len - 1);
    free(str);
    return result;
}

JNI_FUNC(jstring, PdfiumCore, nativeGetDocumentMetaText)(JNI_ARGS, jlong docPtr, jstring tag) {
    const char *ctag = env->GetStringUTFChars(tag, NULL);
    if (ctag == NULL) {
        return env->NewStringUTF("");
    }
    DocumentFile *doc = reinterpret_cast<DocumentFile*>(docPtr);

    size_t bufferLen = FPDF_GetMetaText(doc->pdfDocument, ctag, NULL, 0);
    if (bufferLen <= 2) {
        return env->NewStringUTF("");
    }
    std::wstring text;
    FPDF_GetMetaText(doc->pdfDocument, ctag, WriteInto(&text, bufferLen + 1), bufferLen);
    env->ReleaseStringUTFChars(tag, ctag);
    return env->NewString((jchar*) text.c_str(), bufferLen / 2 - 1);
}

JNI_FUNC(jobject, PdfiumCore, nativeGetFirstChildBookmark)(JNI_ARGS, jlong docPtr, jobject bookmarkPtr) {
    DocumentFile *doc = reinterpret_cast<DocumentFile*>(docPtr);
    FPDF_BOOKMARK parent;
    if(bookmarkPtr == NULL) {
        parent = NULL;
    } else {
        jclass longClass = env->GetObjectClass(bookmarkPtr);
        jmethodID longValueMethod = env->GetMethodID(longClass, "longValue", "()J");

        jlong ptr = env->CallLongMethod(bookmarkPtr, longValueMethod);
        parent = reinterpret_cast<FPDF_BOOKMARK>(ptr);
    }
    FPDF_BOOKMARK bookmark = FPDFBookmark_GetFirstChild(doc->pdfDocument, parent);
    if (bookmark == NULL) {
        return NULL;
    }
    return NewLong(env, reinterpret_cast<jlong>(bookmark));
}

JNI_FUNC(jobject, PdfiumCore, nativeGetSiblingBookmark)(JNI_ARGS, jlong docPtr, jlong bookmarkPtr) {
    DocumentFile *doc = reinterpret_cast<DocumentFile*>(docPtr);
    FPDF_BOOKMARK parent = reinterpret_cast<FPDF_BOOKMARK>(bookmarkPtr);
    FPDF_BOOKMARK bookmark = FPDFBookmark_GetNextSibling(doc->pdfDocument, parent);
    if (bookmark == NULL) {
        return NULL;
    }
    return NewLong(env, reinterpret_cast<jlong>(bookmark));
}

JNI_FUNC(jstring, PdfiumCore, nativeGetBookmarkTitle)(JNI_ARGS, jlong bookmarkPtr) {
    FPDF_BOOKMARK bookmark = reinterpret_cast<FPDF_BOOKMARK>(bookmarkPtr);
    size_t bufferLen = FPDFBookmark_GetTitle(bookmark, NULL, 0);
    if (bufferLen <= 2) {
        return env->NewStringUTF("");
    }
    std::wstring title;
    FPDFBookmark_GetTitle(bookmark, WriteInto(&title, bufferLen + 1), bufferLen);
    return env->NewString((jchar*) title.c_str(), bufferLen / 2 - 1);
}

JNI_FUNC(jlong, PdfiumCore, nativeGetBookmarkDestIndex)(JNI_ARGS, jlong docPtr, jlong bookmarkPtr) {
    DocumentFile *doc = reinterpret_cast<DocumentFile*>(docPtr);
    FPDF_BOOKMARK bookmark = reinterpret_cast<FPDF_BOOKMARK>(bookmarkPtr);

    FPDF_DEST dest = FPDFBookmark_GetDest(doc->pdfDocument, bookmark);
    if (dest == NULL) {
        return -1;
    }
    return (jlong) FPDFDest_GetDestPageIndex(doc->pdfDocument, dest);
}

JNI_FUNC(jlongArray, PdfiumCore, nativeGetPageLinks)(JNI_ARGS, jlong pagePtr) {
    FPDF_PAGE page = reinterpret_cast<FPDF_PAGE>(pagePtr);
    int pos = 0;
    std::vector<jlong> links;
    FPDF_LINK link;
    while (FPDFLink_Enumerate(page, &pos, &link)) {
        links.push_back(reinterpret_cast<jlong>(link));
    }

    jlongArray result = env->NewLongArray(links.size());
    env->SetLongArrayRegion(result, 0, links.size(), &links[0]);
    return result;
}

JNI_FUNC(jobject, PdfiumCore, nativeGetDestPageIndex)(JNI_ARGS, jlong docPtr, jlong linkPtr) {
    DocumentFile *doc = reinterpret_cast<DocumentFile*>(docPtr);
    FPDF_LINK link = reinterpret_cast<FPDF_LINK>(linkPtr);
    FPDF_DEST dest = FPDFLink_GetDest(doc->pdfDocument, link);
    if (dest == NULL) {
        return NULL;
    }
    unsigned long index = FPDFDest_GetDestPageIndex(doc->pdfDocument, dest);
    return NewInteger(env, (jint) index);
}

//JNI_FUNC(jstring, PdfiumCore, nativeGetLinkText)(JNI_ARGS, jlong docPtr, jlong linkPtr){
//    DocumentFile *doc = reinterpret_cast<DocumentFile*>(docPtr);
//    FPDF_LINK link = reinterpret_cast<FPDF_LINK>(linkPtr);
//
//
//
//    size_t bufferLen = FPDFAction_GetURIPath(doc->pdfDocument, action, NULL, 0);
//    if (bufferLen <= 0) {
//        return env->NewStringUTF("");
//    }
//    std::string uri;
//    FPDFAction_GetURIPath(doc->pdfDocument, action, WriteInto(&uri, bufferLen), bufferLen);
//    return env->NewStringUTF(uri.c_str());
//}

JNI_FUNC(jstring, PdfiumCore, nativeGetLinkURI)(JNI_ARGS, jlong docPtr, jlong linkPtr){
    DocumentFile *doc = reinterpret_cast<DocumentFile*>(docPtr);
    FPDF_LINK link = reinterpret_cast<FPDF_LINK>(linkPtr);
    FPDF_ACTION action = FPDFLink_GetAction(link);
    if (action == NULL) {
        return NULL;
    }
    size_t bufferLen = FPDFAction_GetURIPath(doc->pdfDocument, action, NULL, 0);
    if (bufferLen <= 0) {
        return env->NewStringUTF("");
    }
    std::string uri;
    FPDFAction_GetURIPath(doc->pdfDocument, action, WriteInto(&uri, bufferLen), bufferLen);
    return env->NewStringUTF(uri.c_str());
}

JNI_FUNC(jobject, PdfiumCore, nativeGetLinkRect)(JNI_ARGS, jlong linkPtr) {
    FPDF_LINK link = reinterpret_cast<FPDF_LINK>(linkPtr);
    FS_RECTF fsRectF;
    FPDF_BOOL result = FPDFLink_GetAnnotRect(link, &fsRectF);

    if (!result) {
        return NULL;
    }

    jclass clazz = env->FindClass("android/graphics/RectF");
    jmethodID constructorID = env->GetMethodID(clazz, "<init>", "(FFFF)V");
    return env->NewObject(clazz, constructorID, fsRectF.left, fsRectF.top, fsRectF.right, fsRectF.bottom);
}

JNI_FUNC(jobject, PdfiumCore, nativePageCoordsToDevice)(JNI_ARGS, jlong pagePtr, jint startX, jint startY, jint sizeX,
                                            jint sizeY, jint rotate, jdouble pageX, jdouble pageY) {
    FPDF_PAGE page = reinterpret_cast<FPDF_PAGE>(pagePtr);
    int deviceX, deviceY;

    FPDF_PageToDevice(page, startX, startY, sizeX, sizeY, rotate, pageX, pageY, &deviceX, &deviceY);

    jclass clazz = env->FindClass("android/graphics/Point");
    jmethodID constructorID = env->GetMethodID(clazz, "<init>", "(II)V");
    return env->NewObject(clazz, constructorID, deviceX, deviceY);
}

}//extern C
