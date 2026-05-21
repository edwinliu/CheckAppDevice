
#ifndef KECHENG2_SANDBOXFS_H
#define KECHENG2_SANDBOXFS_H

#include <string>
#include <errno.h>
#include <stdlib.h>
#include <cstring>
#include "log.h"
#include "canonicalize_md.h"
#include "BinarySyscallFinder.h"
#define KEY_MAX 256

typedef struct PathItem {
    char *path;   //保存路径的
    bool is_folder;  //是不是一个文件夹
    size_t size; //path 的大小
} PathItem;

typedef struct ReplaceItem {
    char *new_path;
    size_t new_size;
    char *orig_path;
    size_t orig_size;
    bool is_folder;
} ReplaceItem;
enum RelocateResult {
    MATCH,
    NOT_MATCH,
    FORBID,
    KEEP
};
bool isReadOnly(const char * path);

const char *relocate_path(const char *path, char *const buffer, const size_t size);

const char *reverse_relocate_path(const char *path, char *const buffer, const size_t size);

int reverse_relocate_path_inplace(char *const path, const size_t size);

int add_keep_item(const char *path);

int add_forbidden_item(const char *path);

int add_replace_item(const char *orig_path, const char *new_path);

int add_readonly_item(const char *path);

PathItem *get_keep_items();

PathItem *get_forbidden_items();

ReplaceItem *get_replace_items();

ReplaceItem *get_readonly_items();

int get_keep_item_count();

int get_forbidden_item_count();

int get_replace_item_count();



#endif //KECHENG2_SANDBOXFS_H
