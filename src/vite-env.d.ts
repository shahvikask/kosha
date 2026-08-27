// FILE: src/vite-env.d.ts
/// <reference types="vite/client" />

declare module '*?url' {
  const content: string;
  export default content;
}
