package dev.mobarena.config.gui;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class ConfigStepTest {
    @Test void appliesClicksAndClamps() {
        var step=new ConfigStep(60,60,14400);
        assertEquals(2760,step.apply(2700,false,false));
        assertEquals(2640,step.apply(2700,true,false));
        assertEquals(3300,step.apply(2700,false,true));
        assertEquals(2100,step.apply(2700,true,true));
        assertEquals(60,step.apply(60,true,true));
        assertEquals(14400,step.apply(14400,false,true));
        assertEquals(14400,step.apply(Integer.MAX_VALUE,false,true));
    }
    @Test void togglesAndValidates() {
        assertFalse(ConfigStep.toggle(true));
        assertTrue(ConfigStep.toggle(false));
        assertThrows(IllegalArgumentException.class,()->new ConfigStep(0,1,2));
        assertThrows(IllegalArgumentException.class,()->new ConfigStep(1,2,1));
    }
}
