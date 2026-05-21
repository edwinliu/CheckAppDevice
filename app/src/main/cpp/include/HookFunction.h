//
// Created by wiasdfl on 2023/9/16.
//

#ifndef KECHENG2_HOOKFUNCTION_H
#define KECHENG2_HOOKFUNCTION_H
#include "dobby.h"
#include "log.h"



class HookFunction{
public:
    static bool Hooker(void *addr, void *new_func, void **old_func);

};



#endif //KECHENG2_HOOKFUNCTION_H
