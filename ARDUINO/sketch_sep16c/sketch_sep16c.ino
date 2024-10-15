// Borrar para wokwi
#include <Arduino.h>

void get_events();
void start();
void fsm();
void iniciar_caida();
void iniciar_alarma();
void sonar_buzzer();
void verificar_estado_sensor_accelerometro();
void verificar_estado_sensor_boton();
void sonar_alerta();
void activar_alarma();
void prender_led_color();
float mapf(float x, float in_min, float in_max, float out_min, float out_max);
void verificar_estado_bluetooth();
// Fin borrar para wokwi

// Libreria acelerometro
#include <MPU6050.h>
#include "pitches.h"
#include <SoftwareSerial.h>

// ------------------------------------------------
// Etiquetas
// ------------------------------------------------
#define LOG // Comentar esta linea para desactivar logs

// ------------------------------------------------
// Logs
// ------------------------------------------------
void log(const char *estado, const char *evento)
{
#ifdef LOG
    Serial.println("------------------------------------------------");
    Serial.println(estado);
    Serial.println(evento);
    Serial.println("------------------------------------------------");
#endif
}

void log(const char *msg)
{
#ifdef LOG
    Serial.println(msg);
#endif
}

void log(int val)
{
#ifdef LOG
    Serial.println(val);
#endif
}

void log_float(double val)
{
#ifdef LOG
    Serial.println(val);
#endif
}

// ------------------------------------------------
// Constantes
// ------------------------------------------------
#define UMBRAL_CAIDA_MIN 0
#define UMBRAL_CAIDA_MAX 1
#define UMBRAL_PICO_NORMA_CAIDA 3
#define MIN_SENSOR_ACELEROMETRO 0
#define MAX_SENSOR_ACELEROMETRO 4096
#define MIN_ESCALA_ACELEROMETRO_G 0
#define MAX_ESCALA_ACELEROMETRO_G 2
// ------------------------------------------------
// TEMPORIZADORES
// ------------------------------------------------
#define TMP_DELAY_EVENTOS 50
#define TMP_DELAY_BOTON_MILI 50
#define TMP_TIMEOUT_ALARMA 60000
#define TMP_DELAY_NORMA_BLUETOOTH 5000
#define TMP_TIMEOUT_CAIDA_LIBRE 500

// ------------------------------------------------
// Pines sensores (A = analÃ³gico | D = Digital)
// ------------------------------------------------
#define PIN_D_SENSOR_BOTON 2

// ------------------------------------------------
// Pines actuadores (P = PWM | D = Digital)
// ------------------------------------------------
#define PIN_P_ACTUADOR_BUZZER 11
#define PIN_D_ACTUADOR_LED_R 8
#define PIN_D_ACTUADOR_LED_G 12
#define PIN_D_ACTUADOR_LED_B 13

// ------------------------------------------------
// Pines de bluetooth
// ------------------------------------------------
#define PIN_RX_BLUETOOTH 6
#define PIN_TX_BLUETOOTH 7

// ------------------------------------------------
// Estados del embebido
// ------------------------------------------------

enum estado_e
{
    ESTADO_EMBEBIDO_REPOSO,
    ESTADO_EMBEBIDO_CAIDA,
    ESTADO_EMBEBIDO_ALERTA_SONANDO,
    ESTADO_EMBEBIDO_ALERTA_FINALIZADA
};

// ------------------------------------------------
// Eventos posibles
// ------------------------------------------------

enum evento_e
{
    EVENTO_BOTON,
    EVENTO_CAIDA,
    EVENTO_ALARMA,
    EVENTO_TIMEOUT_CAIDA,
    EVENTO_TIMEOUT_ALARMA,
    EVENTO_BLUETOOTH,
    EVENTO_VACIO,
};

// ------------------------------------------------
// Colores posibles
// ------------------------------------------------
enum color_e
{
    COLOR_ROJO,
    COLOR_VERDE,
    COLOR_AMARILLO,
};

// ------------------------------------------------
// Estructura de sensor
// ------------------------------------------------
typedef struct sensor_boton_s
{
    int pin;
    int actual;
    int anterior;
} sensor_boton_t;

typedef struct sensor_mpu_6050_s
{
    MPU6050 sensor;
} sensor_mpu_6050_t;

// ------------------------------------------------
// Estructura de actuador
// ------------------------------------------------
typedef struct actuador_led_s
{
    int pin_r;
    int pin_g;
    int pin_b;
    color_e color;
} actuador_led_t;

typedef struct actuador_buzzer_s
{
    int pin;
} actuador_buzzer_t;

// ------------------------------------------------
// Variables globales
// ------------------------------------------------
estado_e estado_actual;
evento_e evento;
actuador_led_t actuador_led;
actuador_buzzer_t actuador_buzzer;
sensor_boton_t sensor_boton;
sensor_mpu_6050_t sensor_mpu;

unsigned long tiempo_anterior;
unsigned long tiempo_actual;
unsigned long tiempo_delay_boton;
unsigned long tiempo_inicio_alarma;
unsigned long tiempo_inicio_caida;
unsigned long tiempo_delay_bluetooth;
bool verificar_tiempo_alarma;

int nota_actual;
unsigned long tiempo_inicio_nota;
bool alerta_sonando;
bool verificar_caida;

SoftwareSerial bluetooth(PIN_TX_BLUETOOTH,PIN_RX_BLUETOOTH);

void start()
{
    Serial.begin(57600);
    bluetooth.begin(9600);

    // Setup actuadores
    actuador_led.pin_r = PIN_D_ACTUADOR_LED_R;
    actuador_led.pin_g = PIN_D_ACTUADOR_LED_G;
    actuador_led.pin_b = PIN_D_ACTUADOR_LED_B;
    actuador_led.color = COLOR_VERDE;
    pinMode(actuador_led.pin_r, OUTPUT);
    pinMode(actuador_led.pin_g, OUTPUT);
    pinMode(actuador_led.pin_b, OUTPUT);

    actuador_buzzer.pin = PIN_P_ACTUADOR_BUZZER;
    pinMode(actuador_buzzer.pin, OUTPUT);

    // Setup sensores
    sensor_boton.pin = PIN_D_SENSOR_BOTON;
    pinMode(sensor_boton.pin, INPUT_PULLUP);
    sensor_boton.anterior = LOW;

    sensor_mpu.sensor.initialize(ACCEL_FS::A16G, GYRO_FS::G250DPS);

    // Inicializo el temporizador
    tiempo_anterior = millis();

    estado_actual = ESTADO_EMBEBIDO_REPOSO;
    prender_led_color();
    verificar_tiempo_alarma = false;

    alerta_sonando = false;
    nota_actual = 0;
    evento = EVENTO_VACIO;
}

void fsm()
{
    get_events();
    switch (estado_actual)
    {
    case ESTADO_EMBEBIDO_REPOSO:
        switch (evento)
        {
        case EVENTO_BOTON:
        case EVENTO_BLUETOOTH:
            iniciar_alarma();
            estado_actual = ESTADO_EMBEBIDO_ALERTA_SONANDO;
            break;
        case EVENTO_CAIDA:
            iniciar_caida();
            estado_actual = ESTADO_EMBEBIDO_CAIDA;
            break;
        case EVENTO_ALARMA:
        case EVENTO_TIMEOUT_ALARMA:
        case EVENTO_VACIO:
        case EVENTO_TIMEOUT_CAIDA:
        default:
            break;
        }
        break;
    case ESTADO_EMBEBIDO_CAIDA:
        switch (evento)
        {
        case EVENTO_BOTON:
        case EVENTO_BLUETOOTH:
        case EVENTO_ALARMA:
            verificar_caida = false;
            iniciar_alarma();
            estado_actual = ESTADO_EMBEBIDO_ALERTA_SONANDO;
            break;
        case EVENTO_TIMEOUT_CAIDA:
            verificar_caida = false;
            actuador_led.color = COLOR_VERDE;
            prender_led_color();
            estado_actual = ESTADO_EMBEBIDO_REPOSO;
            break;
        case EVENTO_TIMEOUT_ALARMA:
        case EVENTO_VACIO:
        case EVENTO_CAIDA:
        default:
            break;
        }
        break;
    case ESTADO_EMBEBIDO_ALERTA_SONANDO:
        switch (evento)
        {
        case EVENTO_BOTON:
        case EVENTO_BLUETOOTH:
        case EVENTO_TIMEOUT_ALARMA:
            verificar_tiempo_alarma = false;
            estado_actual = ESTADO_EMBEBIDO_ALERTA_FINALIZADA;
            break;
        case EVENTO_VACIO:
            sonar_alerta();
            break;
        case EVENTO_ALARMA:
        case EVENTO_CAIDA:
        case EVENTO_TIMEOUT_CAIDA:
        default:
            break;
        }
        break;
    case ESTADO_EMBEBIDO_ALERTA_FINALIZADA:
        switch (evento)
        {
        case EVENTO_VACIO:
            actuador_led.color = COLOR_VERDE;
            prender_led_color();
            estado_actual = ESTADO_EMBEBIDO_REPOSO;
            break;
        case EVENTO_BLUETOOTH:
        case EVENTO_ALARMA:
        case EVENTO_BOTON:
        case EVENTO_CAIDA:
        case EVENTO_TIMEOUT_ALARMA:
        case EVENTO_TIMEOUT_CAIDA:
        default:
            break;
        }
        break;
    default:
        break;
    }

    // Ya se atendio el evento

    evento = EVENTO_VACIO;
}

int indice = 0;
void (*verificar_sensor[3])() = {verificar_estado_sensor_boton, verificar_estado_sensor_accelerometro, verificar_estado_bluetooth};

void get_events()
{
    tiempo_actual = millis();

    if (verificar_tiempo_alarma)
    {
        if (tiempo_actual - tiempo_inicio_alarma > TMP_TIMEOUT_ALARMA)
        {
            evento = EVENTO_TIMEOUT_ALARMA;
            return;
        }
    }

    if (verificar_caida)
    {
        if (tiempo_actual - tiempo_inicio_caida > TMP_TIMEOUT_CAIDA_LIBRE)
        {
            evento = EVENTO_TIMEOUT_CAIDA;
            return;
        }
    }

    if (tiempo_actual - tiempo_anterior > TMP_DELAY_EVENTOS)
    {
        verificar_sensor[indice]();
        indice = ++indice % 3;
        tiempo_anterior = tiempo_actual;
    }
}

void verificar_estado_sensor_accelerometro()
{
    int16_t accel_x;
    int16_t accel_y;
    int16_t accel_z;
    sensor_mpu.sensor.getAcceleration(&accel_x, &accel_y, &accel_z);
    
    float mapped_accel_x = mapf(accel_x, MIN_SENSOR_ACELEROMETRO, MAX_SENSOR_ACELEROMETRO, MIN_ESCALA_ACELEROMETRO_G, MAX_ESCALA_ACELEROMETRO_G);
    float mapped_accel_y = mapf(accel_y, MIN_SENSOR_ACELEROMETRO, MAX_SENSOR_ACELEROMETRO, MIN_ESCALA_ACELEROMETRO_G, MAX_ESCALA_ACELEROMETRO_G);
    float mapped_accel_z = mapf(accel_z, MIN_SENSOR_ACELEROMETRO, MAX_SENSOR_ACELEROMETRO, MIN_ESCALA_ACELEROMETRO_G, MAX_ESCALA_ACELEROMETRO_G);

    float norma_accel = sqrt((pow(mapped_accel_x, 2) + pow(mapped_accel_y, 2) + pow(mapped_accel_z, 2)));

    if(tiempo_actual - tiempo_delay_bluetooth > TMP_DELAY_NORMA_BLUETOOTH) {
      char norma_string[8];
      sprintf(norma_string, "%d\n\0", norma_accel);
      tiempo_delay_bluetooth = tiempo_actual;

      bluetooth.write(norma_string);
    }

    if (!verificar_caida && norma_accel > UMBRAL_CAIDA_MIN && norma_accel < UMBRAL_CAIDA_MAX)
    {
        evento = EVENTO_CAIDA;
        return;
    }
    else
    {
        if (norma_accel > UMBRAL_PICO_NORMA_CAIDA)
        {
            evento = EVENTO_ALARMA;
            return;
        }
        evento = EVENTO_VACIO;
    }
}

void verificar_estado_sensor_boton()
{
    int lectura_boton = digitalRead(sensor_boton.pin);

    if (lectura_boton != sensor_boton.anterior)
    {
        tiempo_delay_boton = tiempo_actual;
    }

    if (tiempo_actual - tiempo_delay_boton > TMP_DELAY_BOTON_MILI)
    {
        if (lectura_boton != sensor_boton.actual)
        {
            sensor_boton.actual = lectura_boton;
            // Boton oprimido
            if (lectura_boton == LOW)
            {
            }
            // Boton soltado
            else
            {
                evento = EVENTO_BOTON;
            }
        }
    }
    sensor_boton.anterior = lectura_boton;
}

void verificar_estado_bluetooth() {
    if(bluetooth.available() > 0) {
        char c = bluetooth.read();
        if(c == 'a') {
            evento = EVENTO_BLUETOOTH;
        }
    }
}

void prender_led_color()
{
    switch (actuador_led.color)
    {
    case COLOR_ROJO:
        digitalWrite(actuador_led.pin_r, HIGH);
        digitalWrite(actuador_led.pin_g, LOW);
        digitalWrite(actuador_led.pin_b, LOW);
        break;
    case COLOR_VERDE:
        digitalWrite(actuador_led.pin_r, LOW);
        digitalWrite(actuador_led.pin_g, HIGH);
        digitalWrite(actuador_led.pin_b, LOW);
        break;
    case COLOR_AMARILLO:
        digitalWrite(actuador_led.pin_r, HIGH);
        digitalWrite(actuador_led.pin_g, HIGH);
        digitalWrite(actuador_led.pin_b, LOW);
    default:
        break;
    }
}

void sonar_alerta()
{
    unsigned long ahora = millis();
    if (nota_actual < sizeof(duraciones) / sizeof(int))
    {
        int duracion = 1000 / duraciones[nota_actual] * 0.5; // Calcula la duraciÃ³n de la nota

        if (!alerta_sonando)
        {
            tone(actuador_buzzer.pin, melodia[nota_actual], duracion); // Inicia la nueva nota con duraciÃ³n
            tiempo_inicio_nota = ahora;                                // Almacena el tiempo de inicio de la nota
            alerta_sonando = true;                                     // Indica que una nota estÃ¡ en reproducciÃ³n
        }
        else
        {
            if (ahora - tiempo_inicio_nota >= duracion)
            {                                // Si ha pasado el tiempo de duraciÃ³n de la nota
                noTone(actuador_buzzer.pin); // DetÃ©n el tono actual
                nota_actual++;               // Avanza a la siguiente nota
                alerta_sonando = false;      // Indica que la nota terminÃ³
            }
        }
    }
    else
    {
        nota_actual = 0;
    }
}

void iniciar_alarma()
{
    tiempo_inicio_alarma = tiempo_actual;
    actuador_led.color = COLOR_ROJO;
    prender_led_color();
    verificar_tiempo_alarma = true;
}

void iniciar_caida()
{
    verificar_caida = true;
    tiempo_inicio_caida = tiempo_actual;
    actuador_led.color = COLOR_AMARILLO;
    prender_led_color();
}

float mapf(float x, float in_min, float in_max, float out_min, float out_max)
{
    return (x - in_min) * (out_max - out_min) / (in_max - in_min) + out_min;
}

void setup()
{
    start();
}

void loop()
{
    fsm();
}