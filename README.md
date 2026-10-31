# Calco

App para Android que crea stickers de WhatsApp, fijos y animados, sin publicidad.
Negro y blanco, tipografía tipo Times New Roman y bordes rectos, como el reproductor Música.

## Conseguir el APK

Cada cambio en `main` compila la app solo (pestaña **Actions**, unos 5 minutos).
El APK queda en **Releases** → la versión más nueva → `Calco.apk`. Abrilo desde el celular
y aceptá "instalar apps de origen desconocido".

## Qué hace

- **Pegatina:** desde una foto. Girar, voltear, zoom, recorte cuadrado o en círculo,
  quitar el fondo (automático o tocándolo) y borrador a mano.
- **Pegatinas animadas:** desde un video o GIF. Elegís hasta 8 segundos.
- **Decorar:** texto con contorno y borde blanco alrededor del dibujo.
- **Añadir a WhatsApp:** con 3 stickers o más, el botón abre WhatsApp y suma el paquete.
  Cada sticker lleva hasta 3 emojis (WhatsApp los usa para sugerirlo).

Los stickers salen en WebP de 512 × 512, con la calidad ajustada para entrar en los
límites de WhatsApp (100 kB los fijos, 500 kB los animados).

## Cómo está hecha

- `app/src/main/assets/index.html`: toda la interfaz y el editor (HTML + JavaScript).
- `MainActivity.kt`: muestra la interfaz y le da acceso a la galería.
- `StickerContentProvider.kt`: lo que lee WhatsApp para importar los paquetes.

Los paquetes se guardan dentro de la app. Si la desinstalás se borran; actualizarla
con un APK nuevo no los toca (todos se firman con la misma clave, `app/calco.jks`).

La fuente es Liberation Serif (licencia SIL OFL, ver `FONT_LICENSE.txt`).
