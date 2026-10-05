/* Android builds use one daemon per isolated folder; POSIX mqueue IPC is disabled. */
#include <errno.h>
#include "mtp.h"
int msgqueue_handler_init(mtp_ctx *ctx) { ctx->msgqueue_id = -1; return 0; }
int msgqueue_handler_deinit(mtp_ctx *ctx) { return 0; }
int get_message_queue(int create) { errno = ENOSYS; return -1; }
int send_message_queue(char *message) { errno = ENOSYS; return -1; }
int mq_close(int fd) { return 0; }
