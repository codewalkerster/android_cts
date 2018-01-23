/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
#ifndef __CMD_H__
#define __CMD_H__

#define _IOC_NRBITS     8
#define _IOC_TYPEBITS   8

#ifndef _IOC_SIZEBITS
# define _IOC_SIZEBITS  14
#endif

#ifndef _IOC_DIRBITS
# define _IOC_DIRBITS   2
#endif

#define _IOC_NRMASK     ((1 << _IOC_NRBITS)-1)
#define _IOC_TYPEMASK   ((1 << _IOC_TYPEBITS)-1)
#define _IOC_SIZEMASK   ((1 << _IOC_SIZEBITS)-1)
#define _IOC_DIRMASK    ((1 << _IOC_DIRBITS)-1)

#define _IOC_NRSHIFT    0
#define _IOC_TYPESHIFT  (_IOC_NRSHIFT+_IOC_NRBITS)
#define _IOC_SIZESHIFT  (_IOC_TYPESHIFT+_IOC_TYPEBITS)
#define _IOC_DIRSHIFT   (_IOC_SIZESHIFT+_IOC_SIZEBITS)

#ifndef _IOC_NONE
# define _IOC_NONE      0U
#endif

#ifndef _IOC_WRITE
# define _IOC_WRITE     1U
#endif

#ifndef _IOC_READ
# define _IOC_READ      2U
#endif

#define _IOC_TYPECHECK(t) (sizeof(t))
#define _IOC(dir,type,nr,size) \
        (((dir)  << _IOC_DIRSHIFT) | \
         ((type) << _IOC_TYPESHIFT) | \
         ((nr)   << _IOC_NRSHIFT) | \
         ((size) << _IOC_SIZESHIFT))


#define _IO(type,nr)            _IOC(_IOC_NONE,(type),(nr),0)
#define _IOR(type,nr,size)      _IOC(_IOC_READ,(type),(nr),(_IOC_TYPECHECK(size)))
#define _IOW(type,nr,size)      _IOC(_IOC_WRITE,(type),(nr),(_IOC_TYPECHECK(size)))
#define _IOWR(type,nr,size)     _IOC(_IOC_READ|_IOC_WRITE,(type),(nr),(_IOC_TYPECHECK(size)))



struct mult_factor {
	uint32_t numer;
	uint32_t denom;
};

struct mdp_rotation_buf_info {
	uint32_t width;
	uint32_t height;
	uint32_t format;
	struct mult_factor comp_ratio;
};

struct mdp_rotation_config {
	uint32_t	version;
	uint32_t	session_id;
	struct mdp_rotation_buf_info	input;
	struct mdp_rotation_buf_info	output;
	uint32_t	frame_rate;
	uint32_t	flags;
	uint32_t	reserved[6];
};

#define MDSS_ROTATOR_IOCTL_MAGIC 'w'

#define MDSS_ROTATION_OPEN \
	_IOWR(MDSS_ROTATOR_IOCTL_MAGIC, 1, struct mdp_rotation_config *)

#define MDSS_ROTATION_CONFIG \
	_IOWR(MDSS_ROTATOR_IOCTL_MAGIC, 2, struct mdp_rotation_config *)

#define MDSS_ROTATION_CLOSE	_IOW(MDSS_ROTATOR_IOCTL_MAGIC, 4, unsigned int)
#endif
