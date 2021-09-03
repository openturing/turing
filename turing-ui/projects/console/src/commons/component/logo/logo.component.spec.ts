import { ComponentFixture, TestBed, waitForAsync } from '@angular/core/testing';

import { TurLogoComponent } from './logo.component';

describe('TurLogoComponent', () => {
  let component: TurLogoComponent;
  let fixture: ComponentFixture<TurLogoComponent>;

  beforeEach(waitForAsync(() => {
    TestBed.configureTestingModule({
      declarations: [ TurLogoComponent ]
    })
    .compileComponents();
  }));

  beforeEach(() => {
    fixture = TestBed.createComponent(TurLogoComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
