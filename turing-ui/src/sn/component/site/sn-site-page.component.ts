import { Component, ElementRef, OnInit, ViewChild } from '@angular/core';
import { Observable } from 'rxjs';
import { TurSNSite } from '../../model/sn-site.model';
import { NotifierService } from 'angular-notifier';
import { TurSNSiteService } from '../../service/sn-site.service';
import { ActivatedRoute, Router } from '@angular/router';
import { TurLocale } from 'src/locale/model/locale.model';
import { TurLocaleService } from 'src/locale/service/locale.service';
import { TurSEInstance } from 'src/se/model/se-instance.model';
import { TurSEInstanceService } from 'src/se/service/se-instance.service';
import { TurNLPInstance } from 'src/nlp/model/nlp-instance.model';
import { TurNLPInstanceService } from 'src/nlp/service/nlp-instance.service';

@Component({
  selector: 'sn-site-page',
  templateUrl: './sn-site-page.component.html'
})
export class TurSNSitePageComponent implements OnInit {
  @ViewChild('modalDelete') modalDelete: ElementRef;
  private turSNSite: Observable<TurSNSite>;
  private turLocales: Observable<TurLocale[]>;
  private turSEInstances: Observable<TurSEInstance[]>;
  private turNLPInstances: Observable<TurNLPInstance[]>;
  private id: string;
  constructor(private readonly notifier: NotifierService,
    private turSNSiteService: TurSNSiteService,
    private turLocaleService: TurLocaleService,
    private turSEInstanceService: TurSEInstanceService,
    private turNLPInstanceService: TurNLPInstanceService,
    private activatedRoute: ActivatedRoute,
    private router: Router) {
    this.turLocales = turLocaleService.query();
    this.turSEInstances = turSEInstanceService.query();
    this.turNLPInstances = turNLPInstanceService.query();
    this.id = this.activatedRoute.snapshot.paramMap.get('id');
    this.turSNSite = this.turSNSiteService.get(this.id);

  }
  getId(): string {
    return this.id;
  }
  getTurSNSite(): Observable<TurSNSite> {
    return this.turSNSite;
  }

  getTurLocales(): Observable<TurLocale[]> {

    return this.turLocales;
  }

  getTurSEInstances(): Observable<TurSEInstance[]> {

    return this.turSEInstances;
  }
  ngOnInit(): void {
  }

  getTurNLPInstances(): Observable<TurNLPInstance[]> {

    return this.turNLPInstances;
  }

  public save(_turSNSite: TurSNSite) {
    this.turSNSiteService.save(_turSNSite).subscribe(
      (turSNSite: TurSNSite) => {
        _turSNSite = turSNSite;
        this.notifier.notify("success", turSNSite.name.concat(" semantic navigation site was updated."));
      },
      response => {
        this.notifier.notify("error", "SN site was error: " + response);
      },
      () => {
        // console.log('The POST observable is now completed.');
      });

  }

  public delete(_turSNSite: TurSNSite) {
    this.turSNSiteService.delete(_turSNSite).subscribe(
      (turSNSite: TurSNSite) => {
        this.notifier.notify("success", _turSNSite.name.concat(" semantic navigation site was deleted."));
        this.modalDelete.nativeElement.removeAttribute("open");
        this.router.navigate(['/console/sn/site']);
      },
      response => {
        this.notifier.notify("error", "SN site was error: " + response);
      },
      () => {
        // console.log('The POST observable is now completed.');
      });

  }
}
