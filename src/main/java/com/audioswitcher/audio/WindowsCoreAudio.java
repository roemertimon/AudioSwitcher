package com.audioswitcher.audio;

import com.sun.jna.Function;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.WString;
import com.sun.jna.platform.win32.WTypes;
import com.sun.jna.platform.win32.Guid.GUID;
import com.sun.jna.platform.win32.Ole32;
import com.sun.jna.platform.win32.WinNT.HRESULT;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.ptr.PointerByReference;
import com.audioswitcher.model.AudioDevice;

import java.util.ArrayList;
import java.util.List;

/**
 * Native Windows CoreAudio & IPolicyConfig wrapper via JNA COM vtable calls.
 */
public class WindowsCoreAudio {

    public static final int CLSCTX_ALL = WTypes.CLSCTX_ALL;

    // EDataFlow constants
    public static final int eRender = 0; // Playback
    public static final int eCapture = 1; // Recording
    public static final int eAll = 2;

    // ERole constants
    public static final int eConsole = 0; // Default playback
    public static final int eMultimedia = 1; // Media / Games
    public static final int eCommunications = 2; // Voice chat / Calls

    // Device state
    public static final int DEVICE_STATE_ACTIVE = 1;

    // Storage access
    public static final int STGM_READ = 0;

    // CLSID & IID for MMDeviceEnumerator
    private static final GUID CLSID_MMDeviceEnumerator = new GUID("{BCDE0395-E52F-467C-8E3D-C4579291692E}");
    private static final GUID IID_IMMDeviceEnumerator = new GUID("{A95664D2-9614-4F35-A746-DE8DB63617E6}");

    // CLSID for CPolicyConfigClient & IID for IPolicyConfig
    // Windows 10/11 PolicyConfig Client
    private static final GUID CLSID_CPolicyConfigClient = new GUID("{870af99c-171d-4f9e-af0d-e63df40c2bc9}");
    private static final GUID IID_IPolicyConfig = new GUID("{f8679f50-850a-41cf-9c72-430f290290c8}");

    // Alternative GUIDs across Windows builds
    private static final GUID CLSID_CPolicyConfigVista = new GUID("{294fcb26-857a-4d7a-8ab4-3d0882f05634}");
    private static final GUID IID_IPolicyConfig10 = new GUID("{ca286fc3-91fd-42c3-8e9b-caafa66242e3}");

    // PKEY_Device_FriendlyName: {a45c254e-df1c-4efd-8020-67d146a850e0}, 14
    private static final GUID PKEY_Device_FriendlyName_GUID = new GUID("{a45c254e-df1c-4efd-8020-67d146a850e0}");
    private static final int PKEY_Device_FriendlyName_PID = 14;

    private static Pointer callVTable(Pointer pInterface, int index) {
        Pointer vTable = pInterface.getPointer(0);
        return vTable.getPointer((long) index * Native.POINTER_SIZE);
    }

    private static void release(Pointer pInterface) {
        if (pInterface != null) {
            try {
                Pointer funcPtr = callVTable(pInterface, 2); // IUnknown::Release
                Function.getFunction(funcPtr).invoke(int.class, new Object[]{pInterface});
            } catch (Exception ignored) {
            }
        }
    }

    /**
     * Enumerates all active audio playback devices.
     */
    public static List<AudioDevice> getPlaybackDevices() {
        List<AudioDevice> devices = new ArrayList<>();
        Ole32.INSTANCE.CoInitializeEx(null, Ole32.COINIT_APARTMENTTHREADED);

        Pointer pEnumerator = null;
        Pointer pCollection = null;
        try {
            PointerByReference pbrEnumerator = new PointerByReference();
            HRESULT hr = Ole32.INSTANCE.CoCreateInstance(
                    CLSID_MMDeviceEnumerator,
                    null,
                    CLSCTX_ALL,
                    IID_IMMDeviceEnumerator,
                    pbrEnumerator
            );

            if (hr.intValue() != 0 || pbrEnumerator.getValue() == null) {
                System.err.println("CoCreateInstance MMDeviceEnumerator failed: hr=" + hr.intValue());
                return devices;
            }
            pEnumerator = pbrEnumerator.getValue();

            // Find default playback device ID
            String defaultPlaybackId = getDefaultEndpointId(pEnumerator, eConsole);
            String defaultCommunicationId = getDefaultEndpointId(pEnumerator, eCommunications);

            // EnumAudioEndpoints(eRender, DEVICE_STATE_ACTIVE, &pCollection) -> Slot 3
            PointerByReference pbrCollection = new PointerByReference();
            Pointer pEnumAudioEndpoints = callVTable(pEnumerator, 3);
            int res = (int) Function.getFunction(pEnumAudioEndpoints).invoke(int.class, new Object[]{
                    pEnumerator, eRender, DEVICE_STATE_ACTIVE, pbrCollection
            });

            if (res != 0 || pbrCollection.getValue() == null) {
                return devices;
            }
            pCollection = pbrCollection.getValue();

            // GetCount(&count) -> Slot 3 on IMMDeviceCollection
            IntByReference pCount = new IntByReference();
            Pointer pGetCount = callVTable(pCollection, 3);
            Function.getFunction(pGetCount).invoke(int.class, new Object[]{pCollection, pCount});
            int count = pCount.getValue();

            // Item(index, &pDevice) -> Slot 4 on IMMDeviceCollection
            Pointer pItem = callVTable(pCollection, 4);
            for (int i = 0; i < count; i++) {
                PointerByReference pbrDevice = new PointerByReference();
                int itemRes = (int) Function.getFunction(pItem).invoke(int.class, new Object[]{
                        pCollection, i, pbrDevice
                });

                if (itemRes == 0 && pbrDevice.getValue() != null) {
                    Pointer pDevice = pbrDevice.getValue();
                    try {
                        String id = getDeviceId(pDevice);
                        String name = getDeviceFriendlyName(pDevice);

                        boolean isDefault = id != null && id.equalsIgnoreCase(defaultPlaybackId);
                        boolean isComm = id != null && id.equalsIgnoreCase(defaultCommunicationId);

                        if (id != null && name != null) {
                            devices.add(new AudioDevice(id, name, isDefault, isComm, true));
                        }
                    } finally {
                        release(pDevice);
                    }
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            release(pCollection);
            release(pEnumerator);
            Ole32.INSTANCE.CoUninitialize();
        }

        return devices;
    }

    private static String getDefaultEndpointId(Pointer pEnumerator, int role) {
        PointerByReference pbrDefault = new PointerByReference();
        // GetDefaultAudioEndpoint(eRender, role, &pEndpoint) -> Slot 4
        Pointer pGetDefault = callVTable(pEnumerator, 4);
        int res = (int) Function.getFunction(pGetDefault).invoke(int.class, new Object[]{
                pEnumerator, eRender, role, pbrDefault
        });

        if (res == 0 && pbrDefault.getValue() != null) {
            Pointer pDefault = pbrDefault.getValue();
            try {
                return getDeviceId(pDefault);
            } finally {
                release(pDefault);
            }
        }
        return null;
    }

    private static String getDeviceId(Pointer pDevice) {
        // IMMDevice::GetId(&pwstrId) -> Slot 5
        PointerByReference pbrId = new PointerByReference();
        Pointer pGetId = callVTable(pDevice, 5);
        int res = (int) Function.getFunction(pGetId).invoke(int.class, new Object[]{
                pDevice, pbrId
        });

        if (res == 0 && pbrId.getValue() != null) {
            Pointer strPtr = pbrId.getValue();
            String id = strPtr.getWideString(0);
            Ole32.INSTANCE.CoTaskMemFree(strPtr);
            return id;
        }
        return null;
    }

    private static String getDeviceFriendlyName(Pointer pDevice) {
        // IMMDevice::OpenPropertyStore(STGM_READ, &pProps) -> Slot 4
        PointerByReference pbrProps = new PointerByReference();
        Pointer pOpenProps = callVTable(pDevice, 4);
        int res = (int) Function.getFunction(pOpenProps).invoke(int.class, new Object[]{
                pDevice, STGM_READ, pbrProps
        });

        if (res == 0 && pbrProps.getValue() != null) {
            Pointer pProps = pbrProps.getValue();
            try {
                // Construct PROPERTYKEY structure in memory: GUID (16 bytes) + DWORD PID (4 bytes)
                com.sun.jna.Memory propKey = new com.sun.jna.Memory(20);
                propKey.write(0, PKEY_Device_FriendlyName_GUID.getPointer().getByteArray(0, 16), 0, 16);
                propKey.setInt(16, PKEY_Device_FriendlyName_PID);

                // PROPVARIANT in memory (16 bytes on 32-bit, 24 bytes on 64-bit)
                com.sun.jna.Memory propVar = new com.sun.jna.Memory(24);
                propVar.clear();

                // IPropertyStore::GetValue(&key, &pv) -> Slot 5
                Pointer pGetValue = callVTable(pProps, 5);
                int pvRes = (int) Function.getFunction(pGetValue).invoke(int.class, new Object[]{
                        pProps, propKey, propVar
                });

                if (pvRes == 0) {
                    short vt = propVar.getShort(0);
                    // VT_LPWSTR = 31 (0x1F)
                    if (vt == 31) {
                        Pointer pwszVal = propVar.getPointer(8);
                        if (pwszVal != null) {
                            return pwszVal.getWideString(0);
                        }
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                release(pProps);
            }
        }
        return "Unknown Audio Device";
    }

    /**
     * Switches the default playback and communications device to the target device ID.
     */
    public static boolean setDefaultPlaybackDevice(String deviceId) {
        if (deviceId == null || deviceId.isBlank()) {
            return false;
        }

        Ole32.INSTANCE.CoInitializeEx(null, Ole32.COINIT_APARTMENTTHREADED);
        boolean success = false;

        try {
            // Attempt 1: Direct COM call to IPolicyConfig
            success = setEndpointViaCom(deviceId);
        } catch (Exception e) {
            System.err.println("Direct COM set endpoint failed: " + e.getMessage());
        } finally {
            Ole32.INSTANCE.CoUninitialize();
        }

        if (!success) {
            // Fallback: Use PowerShell/C# scriptlet
            success = setEndpointViaFallback(deviceId);
        }

        return success;
    }

    private static boolean setEndpointViaCom(String deviceId) {
        Pointer pPolicyConfig = null;
        try {
            PointerByReference pbrPolicy = new PointerByReference();
            HRESULT hr = Ole32.INSTANCE.CoCreateInstance(
                    CLSID_CPolicyConfigClient,
                    null,
                    CLSCTX_ALL,
                    IID_IPolicyConfig,
                    pbrPolicy
            );

            if (hr.intValue() != 0 || pbrPolicy.getValue() == null) {
                // Try alternative CLSID
                hr = Ole32.INSTANCE.CoCreateInstance(
                        CLSID_CPolicyConfigVista,
                        null,
                        CLSCTX_ALL,
                        IID_IPolicyConfig10,
                        pbrPolicy
                );
            }

            if (hr.intValue() != 0 || pbrPolicy.getValue() == null) {
                return false;
            }

            pPolicyConfig = pbrPolicy.getValue();
            WString wDeviceId = new WString(deviceId);

            // Slot 13: SetDefaultEndpoint(PCWSTR wszDeviceId, ERole eRole)
            Pointer pSetDefault = callVTable(pPolicyConfig, 13);
            Function func = Function.getFunction(pSetDefault);

            // Set for eConsole (0), eMultimedia (1), eCommunications (2)
            int hr0 = (int) func.invoke(int.class, new Object[]{pPolicyConfig, wDeviceId, eConsole});
            int hr1 = (int) func.invoke(int.class, new Object[]{pPolicyConfig, wDeviceId, eMultimedia});
            int hr2 = (int) func.invoke(int.class, new Object[]{pPolicyConfig, wDeviceId, eCommunications});

            return hr0 == 0;
        } finally {
            release(pPolicyConfig);
        }
    }

    private static boolean setEndpointViaFallback(String deviceId) {
        try {
            String script = String.format(
                    "$id = '%s';\n" +
                    "$csharp = @'\n" +
                    "using System;\n" +
                    "using System.Runtime.InteropServices;\n" +
                    "[ComImport, Guid(\"870af99c-171d-4f9e-af0d-e63df40c2bc9\")]\n" +
                    "internal class CPolicyConfigClient {}\n" +
                    "[Guid(\"f8679f50-850a-41cf-9c72-430f290290c8\"), InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]\n" +
                    "internal interface IPolicyConfig {\n" +
                    "    void Reserved0(); void Reserved1(); void Reserved2(); void Reserved3();\n" +
                    "    void Reserved4(); void Reserved5(); void Reserved6(); void Reserved7();\n" +
                    "    void Reserved8(); void Reserved9(); void Reserved10();\n" +
                    "    [PreserveSig] int SetDefaultEndpoint([MarshalAs(UnmanagedType.LPWStr)] string devId, int role);\n" +
                    "}\n" +
                    "public class AudioSwitcher {\n" +
                    "    public static int Set(string id) {\n" +
                    "        var p = (IPolicyConfig)new CPolicyConfigClient();\n" +
                    "        p.SetDefaultEndpoint(id, 0);\n" +
                    "        p.SetDefaultEndpoint(id, 1);\n" +
                    "        p.SetDefaultEndpoint(id, 2);\n" +
                    "        return 0;\n" +
                    "    }\n" +
                    "}\n" +
                    "'@\n" +
                    "if (-not ([System.Management.Automation.PSTypeName]'AudioSwitcher').Type) { Add-Type -TypeDefinition $csharp }\n" +
                    "[AudioSwitcher]::Set($id)\n",
                    deviceId.replace("'", "''")
            );

            ProcessBuilder pb = new ProcessBuilder("powershell.exe", "-NoProfile", "-NonInteractive", "-Command", script);
            Process process = pb.start();
            int exitCode = process.waitFor();
            return exitCode == 0;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }
}
