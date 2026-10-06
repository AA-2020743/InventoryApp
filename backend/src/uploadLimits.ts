// The largest image the API accepts. Its own module because two places need
// it - multer enforces it and the error handler quotes it - and those two
// already import each other's neighbours, so housing it in either would make
// a cycle.
export const MAX_IMAGE_BYTES = 8 * 1024 * 1024;
