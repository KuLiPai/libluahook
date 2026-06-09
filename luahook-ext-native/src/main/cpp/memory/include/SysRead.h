#include <stdio.h>
#include <unistd.h>
#include <stdlib.h>
#include <fcntl.h>
#include <dirent.h>
#include <math.h>
#include <iostream>
#include <thread>
#include <sys/syscall.h>
#include <sys/mman.h>
#include <string.h>
#include <stdint.h>

char *GetPackageName() {
    char *PackageName[256];
    FILE *fp = fopen("proc/self/cmdline", "r");
    if (fp) {
        fread(PackageName, sizeof(PackageName), 1, fp);
        fclose(fp);
    }
    return (char *) PackageName;
}

void ReadBuffer(long addr, void *buffer, int size) {
    struct iovec iov_ReadBuffer, iov_ReadOffset;
    iov_ReadBuffer.iov_base = buffer;
    iov_ReadBuffer.iov_len = size;
    iov_ReadOffset.iov_base = (void *) addr;
    iov_ReadOffset.iov_len = size;
    syscall(SYS_process_vm_readv, getpid(), &iov_ReadBuffer, 1, &iov_ReadOffset, 1, 0);
}

void WriteBuffer(long addr, void *buffer, int size) {
    struct iovec iov_WriteBuffer, iov_WriteOffset;
    iov_WriteBuffer.iov_base = buffer;
    iov_WriteBuffer.iov_len = size;
    iov_WriteOffset.iov_base = (void *) addr;
    iov_WriteOffset.iov_len = size;
    syscall(SYS_process_vm_writev, getpid(), &iov_WriteBuffer, 1, &iov_WriteOffset, 1, 0);
}

bool ReadAddr(void *addr, void *buffer, size_t length) {
    unsigned long page_size = sysconf(_SC_PAGESIZE);
    unsigned long size = page_size * sizeof(uintptr_t);
    return mprotect((void *) ((uintptr_t) addr - ((uintptr_t) addr % page_size) - page_size),
                    (size_t) size, PROT_EXEC | PROT_READ | PROT_WRITE) == 0 &&
           memcpy(buffer, addr, length) != 0;
}

bool WriteAddr(void *addr, void *buffer, size_t length) {
    unsigned long page_size = sysconf(_SC_PAGESIZE);
    unsigned long size = page_size * sizeof(uintptr_t);
    return mprotect((void *) ((uintptr_t) addr - ((uintptr_t) addr % page_size) - page_size),
                    (size_t) size, PROT_EXEC | PROT_READ | PROT_WRITE) == 0 &&
           memcpy(addr, buffer, length) != 0;
}

void WriteAddr2(void *addr, void *buffer, size_t length) {
    uintptr_t page_size = 0x2000;
    void *page_start = (void *) ((uintptr_t) addr & ~(page_size - 1));
    mprotect(page_start, page_size, PROT_EXEC | PROT_WRITE);
    memcpy(addr, buffer, length);
}

int ReadDword(long addr) {
    int var = 0;
    ReadBuffer(addr, &var, 4);
    return var;
}

float ReadFloat(long addr) {
    float var = 0;
    ReadBuffer(addr, &var, 4);
    return var;
}

long ReadZZ(long addr) {
    if (addr < 0xFFFFFFFF) {
        long var = 0;
        ReadBuffer(addr & 0xFFFFFFFFFF, &var, 4);
        return var & 0xFFFFFFFFFF;
    } else {
        long var = 0;
        ReadBuffer(addr & 0xFFFFFFFFFF, &var, 8);
        return var & 0xFFFFFFFFFF;
    }
}

int readb(int &c, int num) {
    ++c;
    return num;
}

template<typename...s>
long ReadPoint(long addr, s ... args) {
    int con = 0;
    addr = ReadZZ(addr);
    int arr[] = {(readb(con, args))...};
    for (int f = 0; f < con; f++) {
        if (f == con - 1) {
            addr += arr[f];
            return addr;
        }
        addr = ReadZZ(addr + arr[f]);
    }
}

void WriteDword(long addr, int value) {
    WriteAddr((void *) addr, &value, 4);
}

void WriteDword2(long addr, int value) {
    WriteAddr2((void *) addr, &value, 4);
}

void WriteFloat(long addr, float value) {
    WriteBuffer(addr, &value, 4);
}

void WriteByte(long addr, uint8_t value) {
    WriteBuffer(addr, &value, 1);
}

uint8_t ReadByte(long addr) {
    uint8_t var = 0;
    ReadBuffer(addr, &var, 1);
    return var;
}


uintptr_t GetModuleBase(const char *module_name, const char *module_field) {
    long addr = 0;
    char path[64];
    char line[1024];
    char *isbss;
    int flag = 0;
    module_name = strtok(strdup(module_name), ":");
    isbss = strtok(NULL, ":");
    sprintf(path, "/proc/%d/maps", getpid());
    FILE *fp = fopen(path, "r");
    if (fp) {
        while (fgets(line, sizeof(line), fp)) {
            if (strstr(line, module_name) && strstr(line, module_field)) {
                flag = 1;
                if (!isbss) {
                    sscanf(line, "%lx", &addr);
                    break;
                }
            }
            if (flag == 1 && strstr(line, "[anon:.bss]")) {
                sscanf(line, "%lx", &addr);
                break;
            }
        }
        fclose(fp);
    }
    return addr;
}

char *Unicode(long addr) {
    int len = 0;
    ReadBuffer(addr - 4, &len, 4);
    if (len <= 0 || len > 100) {
        return "";
    }
    char *retres;
    retres = (char *) malloc(sizeof(short) * 50);
    memset(retres, '\0', sizeof(retres));
    uint16_t Namecode[len];
    ReadBuffer(addr, Namecode, len * 2);
    setlocale(LC_ALL, "");
    char str[12];
    for (int i = 0; i < len; i++) {
        wchar_t wstr[] = {Namecode[i], 0};
        wcstombs(str, wstr, sizeof(str));
        strcat(retres, str);
    }
    return retres;
}

void getUTF8(char *utf8Name, long addr)
{
    for (int i = 0; i < 30; i++)
    {
        int value;
        ReadBuffer(addr + i, &value, 1);
        if (value == 0)
        {
            break;
        }
        utf8Name[i] = value & 0xFF;
    }
}

