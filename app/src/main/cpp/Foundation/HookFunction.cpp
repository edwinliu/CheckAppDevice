//
// Created by wiasdfl on 2023/9/16.
//

#include "HookFunction.h"


bool HookFunction::Hooker(void *addr, void *new_func, void **orig_func) {
    bool ret= false;
    ret = DobbyHook(addr,
                    reinterpret_cast<dobby_dummy_func_t>(new_func),
                    reinterpret_cast<dobby_dummy_func_t *>(orig_func)) == 0;

    if (ret){
        return ret;
    }
    LOGE("000000");
    dobby_enable_near_branch_trampoline();  //附近插装hook
    ret = DobbyHook(addr,
                    reinterpret_cast<dobby_dummy_func_t>(new_func),
                    reinterpret_cast<dobby_dummy_func_t *>(orig_func)) == 0;
    dobby_disable_near_branch_trampoline();//关闭插桩
    LOGE("11111");
    if (!ret){

        return ret;
    }
    LOGE("22222");
    return ret;
}