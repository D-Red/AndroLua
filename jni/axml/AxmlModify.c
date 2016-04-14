/*
AXML Ambiguity
author: wanchouchou
Blog: http://www.cnblogs.com/wanyuanchun/
*/

#include "AxmlModify.h"
#include "AxmlParser.h"

/* attribute structure within tag */
typedef struct{
	char uri[4];		/* uri of its namespace */
	char name[4];
	char string[4];	/* attribute value if type == ATTR_STRING */
	char type[4];		/* attribute type, == ATTR_*  ָ������ֵ�����ͣ���string, int, float��*/
	char data[4];		/* attribute value, encoded on type ���Ե�ֵ����ֵ�������Ե� type���б���*/
} Attribute_t;


static void copyDataWithPosChange(FileAssit_t *to, FileAssit_t *src, size_t size){
	memcpy(to->data + to->cur, src->data + src->cur, size);
	to->cur += size;
	src->cur += size;
}

static uint32_t getUint32(FileAssit_t *hp){
	uint32_t value = 0;
	unsigned char *p = hp->data + hp->cur;
	value = p[0] | p[1]<<8 | p[2]<<16 | p[3]<<24;
	hp->cur += 4;
	return value;
}
static void skipUint32(FileAssit_t *hp){
	hp->cur += 4;
}
/*
����˱�ʾ��uint_32ת����С�˱�ʾ��4�ֽ����ݺ󣬴洢��Ŀ���ַ
*/
static void copyUint32(char *to, uint32_t value){
	/**/
	char p[4];
	p[0] = value & 0xff ;
	p[1] = (value >>8) &0xff;
	p[2] = (value >> 16) &0xff;
	p[3] = (value >> 24) &0xff;
	memcpy(to, p, 4);

}

/*
��string�в��������ַ���chouchou.class����name
*/
void modifyStringChunk(FileAssit_t *in, FileAssit_t *out, size_t *externSize){
	char appendString[32] = {0x0E,0x00,0x63,0x00,0x68,0x00,0x6F,0x00,0x75,0x00,0x63,0x00,0x68,0x00,0x6F,0x00,0x75,0x00,
		0x2E,0x00,0x63,0x00,0x6C,0x00,0x61,0x00,0x73,0x00,0x73,0x00,0x00,0x00}; //chouchou.class��UTF-16����
	char appendString2[12] = {0x04,0x00,0x6E,0x00,0x61,0x00,0x6D,0x00,0x65,0x00,0x00,0x00}; //name��UTF-16����
	//���⣬һ��Ҫ����4�ֽڶ�����������ǵ������styleData�Σ���ôԭstring�����û����4�ֽڶ��룬ֱ���ں������string��Ȼ������Ƿ���¼����string����4�ֽڶ��롣
	//���ûstyleData�Σ���ôԭstring����ͽ�����4�ֽڶ��룬����ֻ��Ҫ������ӵ�string length�������Ƿ����4�ֽڶ��뼴�ɡ�
	//����漰��stringchunksize�ĸı��styleOffset�ĸı�,��������ֻ����������ȷ�����ߵĴ�С~
	//��֮��������Ƚ��鷳����Ҫ�ر�ע��
	uint32_t stringChunkSize = 0;
	uint32_t stringCount = 0;
	uint32_t styleCount = 0;
	uint32_t stringOffset = 0;
	uint32_t styleOffset = 0;
	uint32_t stringLen = 0;
	uint32_t addStringOffset = 0;
	uint32_t addStringOffset2 = 0;
	uint32_t addStringOffset_alied = 0;
	uint32_t addStringOffset_alied2 = 0;

	copyDataWithPosChange(out, in, 4); //StringTag
	stringChunkSize = getUint32(in);
	out->cur += 4;
	stringCount = getUint32(in);
	g_curStringCount = stringCount + 2;
	copyUint32(out->data + out->cur, stringCount + 2);
	out->cur += 4;
	styleCount = getUint32(in);
	in->cur -= 4;
	copyDataWithPosChange(out, in, 4*2); //stylesCount �� reverse
	stringOffset = getUint32(in);
    copyUint32(out->data + out->cur, stringOffset + 4*2); //���ڲ�����2���ַ�����������Ҫ�������4�ֽڵ�ƫ����Ŀ������stringOffsetҪ��8��
	out->cur += 4;
	styleOffset = getUint32(in); //styleOffset���ܻ�ı䣬��������Ͳ�ֱ��copy��
	if(styleOffset == 0){ //˵��û��style������ֱ��copy
		in->cur -= 4;
		copyDataWithPosChange(out, in, 4);
		stringLen = stringChunkSize - stringOffset;
	}else{ //˵����style����ô�ڲ���string��styleOffset��ı�
		//styleOffset����ٽ��и�ֵ
		out->cur += 4;
		stringLen = styleOffset - stringOffset;
	}
	//copy stringCount �� string ƫ��ֵ
	copyDataWithPosChange(out, in, stringCount * 4);
	/*Ȼ���ڴ�ʱ��out->data�в���2��stringƫ��ֵ��ָ�����ǲ�����ַ������׵�ַ*/
	addStringOffset = stringLen;
	addStringOffset_alied = (addStringOffset+0x03)&(~0x03);  //��Ȼ��û��style�������stringLen�������4����������������style������¾Ͳ�һ����~
	addStringOffset2 = addStringOffset_alied + 32;  //�����chouchou.class�Ĵ�С
	addStringOffset_alied2 = (addStringOffset2+0x03)&(~0x03); //Ϊ�˷����Ժ���չ������addStringOffset2��Ȼ��4���������������ǽ���һ�ζ����
	*externSize += (addStringOffset_alied - addStringOffset + addStringOffset_alied2 - addStringOffset2);
	//����chochou.class��ƫ��ֵ
	copyUint32(out->data + out->cur, addStringOffset_alied);
	out->cur += 4;
	//����name��ƫ��ֵ
	copyUint32(out->data + out->cur, addStringOffset_alied2);
	out->cur += 4;

	//Ȼ�����������n�� stylesƫ��ֵ��
	copyDataWithPosChange(out, in, styleCount * 4);
	//Ȼ�����string data
	copyDataWithPosChange(out, in, stringLen);

	//���롰chochou.class�� �������ڲ����32�ֽڸպ�Ϊ4�ı��������ԾͲ���Ҫ���ж����ˡ������Ժ���Ӱ�~
	memcpy(out->data + out->cur, appendString, 32);
	out->cur += 32;
	//����"name",ͬ������Ҫ����
	memcpy(out->data + out->cur, appendString2, 12);
	out->cur += 12;

	/*��ӳ��ȸպ�Ϊ4����������������ӵ��ַ��������漰������������Ҫ����������ַ�����������Ӧ���޸ļ���*/
	//chouchou.class
	*externSize += 32; //�ַ�������
	*externSize += 4;  //��ҪΪ���ַ������һ��4�ֽڵ�ƫ�Ʊ���
	//name
	*externSize +=12;
	*externSize += 4;

	if(styleOffset != 0){
		//����sxternSize��ֵ,ȷ����ǰstyleOffsetֵ
		copyUint32(out->data + 0x20,styleOffset + *externSize);
	}
	//����sxternSize��ֵ��ȷ����ǰstringChunck�Ĵ�С
	copyUint32(out->data + 0xc, stringChunkSize + *externSize);

}

void modifyResourceChunk(FileAssit_t *in, FileAssit_t *out, size_t *externSize){
	//���Ȼ�ȡԭʼresourceChunk��С
	uint32_t resChunkSize = 0;
	uint32_t resCount = 0;
	uint32_t needAppendCount = 0;
	resChunkSize = getUint32(in);
	resCount = resChunkSize /4 -2;
	needAppendCount = g_curStringCount - resCount;

	copyUint32(out->data + out->cur, resChunkSize + needAppendCount * 4);
	out->cur += 4;

	//copy ԭ����resCount��resourceID
	copyDataWithPosChange(out, in, resCount * 4);

	//��0x0���ʣ�µ�resourcesID
	memset(out->data + out->cur, 0, needAppendCount * 4);
	out->cur += needAppendCount*4;

	*externSize += (needAppendCount * 4);

}

void modifyAppTagChunk(FileAssit_t *in, FileAssit_t *out, size_t *externSize){
	uint32_t curAttrCount = 0;
	uint32_t curChunkSize = 0;
	Attribute_t attr;

	memset(&attr, 0, sizeof(Attribute_t));
	copyUint32(attr.uri, g_appURIindex);
	copyUint32(attr.name, g_curStringCount - 1);
	copyUint32(attr.string, g_curStringCount - 2);
	copyUint32(attr.type, 0x03000008);
	copyUint32(attr.data,  g_curStringCount - 2);

	//�޸�chunksize!!
	in->cur -= 0x10; //ָ��chunksize;
	curChunkSize = getUint32(in);
	curChunkSize += sizeof(Attribute_t);
	copyUint32(out->data + out->cur - 0x10, curChunkSize);
	in->cur += 0xc; //ָ��tagname

	copyDataWithPosChange(out, in, 8); //tagname and flags
	curAttrCount = getUint32(in);
	curAttrCount++;
	copyUint32(out->data + out->cur, curAttrCount);
	out->cur += 4;
	copyDataWithPosChange(out, in, 4); //classAttribute

	//��������ӵ����Խṹ��
	memcpy(out->data + out->cur, (char*)&attr, sizeof(Attribute_t));
	out->cur += sizeof(Attribute_t);
	*externSize += sizeof(Attribute_t);


	/*������������ڲ��øı䣬����ֱ��copy����*/

}

int axmlModify(char* inbuf, size_t insize, char *out_filename){
	FILE *fp;
	char *outbuf;
	char *filename = out_filename;
	size_t externSize = 0;  //���ŵ��ֽ���
	uint32_t filesize = 0;
	size_t ret = 0;
	FileAssit_t input_h, output_h;
	fp = fopen(filename, "wb");

	if(fp == NULL)
	{
		fprintf(stderr, "Error: open output file failed.\n");
		return -1;
	}
	outbuf = (char *)malloc((insize + 300) * sizeof(char));  //�����300�ֽڣ����Ը����Լ�����ַ����Ĵ�С�������䡣
	if(outbuf == NULL){
		fprintf(stderr, "Error: malloc outbuf failed.\n");
		return -1;
	}
	memset(outbuf, 0, insize);
	input_h.data = inbuf;
	input_h.cur = 0;
	output_h.data = outbuf;
	output_h.cur = 0;
	//����copyħ��
	copyDataWithPosChange(&output_h, &input_h, 4);
	//Ȼ���ȡ�ļ���С
	filesize = getUint32(&input_h);
	output_h.cur += 4;

	//����string������stringChunk��
	modifyStringChunk(&input_h, &output_h, &externSize);
	//style data��g_res_ChunkSizeOffset֮������ݣ����ڲ���Ҫ�Ķ�������ֱ��copy
	copyDataWithPosChange(&output_h, &input_h, g_res_ChunkSizeOffset - input_h.cur);

	//����curStringChunk�Ĵ�С��ResourceChunk�������䣬�Ա�����ӵ�attr��ID��Ϊ0x00000000
	modifyResourceChunk(&input_h, &output_h, &externSize);
	copyDataWithPosChange(&output_h, &input_h, g_appTag_nameOff - input_h.cur);

	//����attrubition�����ĸ�attr������tagChunk��
	modifyAppTagChunk(&input_h, &output_h, &externSize);
	//�޸��ļ���С
	copyUint32(output_h.data + 4, filesize + externSize);
	//copyʣ��Ĳ���
	copyDataWithPosChange(&output_h, &input_h, filesize - input_h.cur);

	ret = fwrite(output_h.data, 1, output_h.cur, fp);
	if(ret != output_h.cur){
		fprintf(stderr, "Error: fwrite outbuf error.\n");
		return -1;
	}

	free(output_h.data);
	fclose(fp);

	return 0;







}
