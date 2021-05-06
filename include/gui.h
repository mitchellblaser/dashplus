/*
DashPlus
Mitchell Blaser 2021.
http://github.com/mitchellblaser/dashplus
*/

#include <raylib.h>
#include <iostream>
#include <stdio.h>
#include <stdlib.h>
#include <chrono>
#include "fonts.h"
#include "timer.h"

using namespace std;

#ifndef DP_GUI
#define DP_GUI

const int MAX_ELEMENTS = 100;

enum ElementTypes {
    Button,
    TextBox
};

class GUI {
    public:
        GUI();
        void Button(int ID, int PosX, int PosY, string ButtonText, int Width, int Height, Color ButtonColor, Font ButtonFont, int FontSize, Color TextColor, void (*onclick)()=NULL);
        void TextBox(int ID, int PosX, int PosY, string PlaceholderText, int Width, int Height, Color BoxColor, Font TextFont, int FontSize, Color TextColor, bool ShowLabel=false, string LabelText="", int LabelPadding=120);
        void MainEventLoop(string DPATH);
        int GetClickedElement();
        string GetTextFromElement(int ID);
        int GetEmptyElementID();
    private:

        class Element {
            public:
                ElementTypes Type;
                string Cache;
                double X1Pos;
                double Y1Pos;
                double X2Pos;
                double Y2Pos;
                bool Initialized = false;

                void (*Event)() = NULL;
        };

        Fonts fonts;
        
        Timer debounceTimer{100, true};
        Timer inputBlinker{750};

        double HighlightBorderSize = 2;

        Element Elements[MAX_ELEMENTS];

        string UserInputBuffer = "";
        int SelectedUserInput = -1;
        bool BlinkerState = false;
        int PreviousBlinkCapture = -1;
};

#endif